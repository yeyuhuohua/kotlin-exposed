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
    private val queue = ArrayBlockingQueue<() -> Unit>(QUEUE_CAPACITY)
    private val accepting = AtomicBoolean(true)
    private val dropped = AtomicLong(0)
    private val consumers = mutableListOf<Thread>()

    @PostConstruct
    fun start() {
        repeat(CONSUMER_COUNT) { index ->
            val thread = Thread({
                while (accepting.get() || queue.isNotEmpty()) {
                    val task = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    runCatching { task.invoke() }
                        .onFailure { log.warn("audit write failed: {}", it.message) }
                }
            }, "audit-writer-$index")
            thread.isDaemon = true
            thread.start()
            consumers.add(thread)
        }
    }

    @PreDestroy
    fun shutdown() {
        accepting.set(false)
        val deadline = System.currentTimeMillis() + SHUTDOWN_DRAIN_MILLIS
        for (thread in consumers) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            runCatching { thread.join(remaining) }
        }
        val lost = queue.size
        if (lost > 0) log.warn("audit shutdown discarded {} queued records", lost)
        queue.clear()
    }

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

    private fun enqueue(task: () -> Unit) {
        if (!accepting.get()) return
        if (!queue.offer(task)) {
            val count = dropped.incrementAndGet()
            if (count == 1L || count % 1000 == 0L) {
                log.warn("audit queue full, dropped {} records so far", count)
            }
        }
    }

    companion object {
        const val QUEUE_CAPACITY = 1000
        const val CONSUMER_COUNT = 2
        const val SHUTDOWN_DRAIN_MILLIS = 5000L
    }
}
