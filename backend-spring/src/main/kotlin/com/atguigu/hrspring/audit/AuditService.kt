package com.atguigu.hrspring.audit

import com.atguigu.hrspring.entity.AuditApiCallEntity
import com.atguigu.hrspring.entity.AuditLoginRecordEntity
import com.atguigu.hrspring.mapper.AuditApiCallMapper
import com.atguigu.hrspring.mapper.AuditLoginRecordMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 审计异步写入:有界队列(1000)+ 2 个消费线程;队列满直接丢弃并计数,
 * 绝不阻塞请求线程。停机时关闭入队,最多等 5 秒排空,取消的在途记录计入丢失。
 */
@Service
class AuditService(
    private val loginRecordMapper: AuditLoginRecordMapper,
    private val apiCallMapper: AuditApiCallMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val queue = ArrayBlockingQueue<AuditTask>(QUEUE_CAPACITY)
    private val accepting = AtomicBoolean(true)
    private val stopping = AtomicBoolean(false)
    private val dropped = AtomicLong(0)
    private val consumers = mutableListOf<Thread>()
    private val inFlight = ConcurrentHashMap.newKeySet<AuditTask>()
    private val lifecycleLock = Any()

    @Volatile
    internal var drainTimeoutMs = SHUTDOWN_DRAIN_MILLIS

    /** 启动后台处理流程。 */
    @PostConstruct
    fun start() {
        repeat(CONSUMER_COUNT) { index ->
            val thread = Thread({ consume() }, "audit-writer-$index")
            thread.isDaemon = true
            thread.start()
            consumers.add(thread)
        }
    }

    /** 持续消费审计队列，退出前尽量写完已排队事件。 */
    private fun consume() {
        while (!stopping.get() && (accepting.get() || queue.isNotEmpty())) {
            val task = try {
                queue.poll(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                if (stopping.get()) break else continue
            } ?: continue
            // 出队任务必须与停止流程互斥登记，确保在途任务只会完成或丢弃一次。
            val accepted = synchronized(lifecycleLock) {
                if (stopping.get()) {
                    task.discard()
                    false
                } else {
                    inFlight += task
                    true
                }
            }
            if (!accepted) continue
            try {
                task.write.invoke()
            } catch (e: Exception) {
                if (isInterruption(e)) {
                    task.discard()
                    Thread.currentThread().interrupt()
                } else {
                    log.warn("audit write failed: {}", e.message)
                }
            } finally {
                task.complete()
                inFlight -= task
            }
            if (Thread.currentThread().isInterrupted) break
        }
    }

    /** 判断异常是否表示线程中断或协程取消。 */
    private fun isInterruption(error: Exception): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is InterruptedException) return true
            current = current.cause
        }
        return false
    }

    /** 停止当前服务并释放其持有的资源。 */
    @PreDestroy
    fun shutdown() {
        synchronized(lifecycleLock) { accepting.set(false) }
        joinConsumers(System.currentTimeMillis() + drainTimeoutMs)
        if (consumers.any { it.isAlive }) {
            synchronized(lifecycleLock) { stopping.set(true) }
            consumers.forEach { it.interrupt() }
            joinConsumers(System.currentTimeMillis() + 1_000)
        }
        inFlight.toSet().forEach { it.discard() }
        val remaining = mutableListOf<AuditTask>()
        queue.drainTo(remaining)
        remaining.forEach { it.discard() }
    }

    /** 等待审计消费者处理完已有任务并退出。 */
    private fun joinConsumers(deadline: Long) {
        for (thread in consumers) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return
            try {
                thread.join(remaining)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** 提交登录审计事件，不阻塞当前登录请求。 */
    fun recordLogin(
        username: String,
        userId: Int?,
        ip: String,
        userAgent: String?,
        success: Boolean,
        errorCode: String?,
        occurredAt: LocalDateTime = LocalDateTime.now(),
    ) {
        enqueue {
            loginRecordMapper.insert(
                AuditLoginRecordEntity().apply {
                    this.username = username.take(64)
                    this.userId = userId
                    this.ip = ip.take(45)
                    this.userAgent = userAgent?.take(255)
                    this.success = success
                    this.errorCode = errorCode?.take(40)
                    this.createdAt = occurredAt
                },
            )
        }
    }

    /** 提交接口调用审计事件，不阻塞当前业务请求。 */
    fun recordApiCall(
        userId: Int?,
        username: String?,
        method: String,
        path: String,
        queryString: String?,
        statusCode: Int,
        durationMs: Long,
        ip: String,
        userAgent: String?,
        occurredAt: LocalDateTime = LocalDateTime.now(),
    ) {
        enqueue {
            apiCallMapper.insert(
                AuditApiCallEntity().apply {
                    this.userId = userId
                    this.username = username?.take(64)
                    this.method = method.take(8)
                    this.path = path.take(255)
                    this.queryString = queryString?.take(255)
                    this.statusCode = statusCode
                    this.durationMs = durationMs
                    this.ip = ip.take(45)
                    this.userAgent = userAgent?.take(255)
                    this.createdAt = occurredAt
                },
            )
        }
    }

    /** 把审计事件写入有界队列，队列满时执行降级处理。 */
    private fun enqueue(write: () -> Unit) {
        synchronized(lifecycleLock) {
            if (!accepting.get()) return
            if (!queue.offer(AuditTask(write))) recordLoss()
        }
    }

    private inner class AuditTask(val write: () -> Unit) {
        private val settled = AtomicBoolean(false)

        /** 标记审计任务完成并唤醒等待中的关闭流程。 */
        fun complete() {
            settled.set(true)
        }

        /** 丢弃无法执行的审计任务并更新完成计数。 */
        fun discard() {
            if (settled.compareAndSet(false, true)) recordLoss()
        }
    }

    /** 记录审计事件丢失数量，避免影响业务请求。 */
    private fun recordLoss() {
        val count = dropped.incrementAndGet()
        if (count == 1L || count % 1000 == 0L) {
            log.warn("audit records dropped, {} so far", count)
        }
    }

    companion object {
        const val QUEUE_CAPACITY = 1000
        const val CONSUMER_COUNT = 2
        const val SHUTDOWN_DRAIN_MILLIS = 5000L
    }
}
