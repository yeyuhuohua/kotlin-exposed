package com.atguigu.hr.audit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * 审计日志的异步写入入口：有界队列 + 固定 worker 消费，写入并发有上限。
 * 数据库变慢导致积压时丢弃新记录并计数告警，绝不让审计拖垮请求处理。
 * 停机时先关闭入队，限时等待消费者排空，超时取消并统计损失。
 * 查询直接透传仓储（审计数据实时变化，不进 Redis 缓存）。
 * 客户端 IP 由 common/api/ClientIp 按可信代理解析，与登录限流共用同一来源。
 */
object AuditService {
    private const val QUEUE_CAPACITY = 1_000
    private const val WORKERS = 2
    private val log = LoggerFactory.getLogger(AuditService::class.java)
    private val lifecycleLock = Any()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queue = Channel<suspend () -> Unit>(QUEUE_CAPACITY)
    private val workers = mutableListOf<Job>()
    private val dropped = AtomicLong(0)

    /** 已取出但尚未写完的记录数；停机超时取消时计入损失。 */
    private val inFlight = AtomicLong(0)

    /** 停机排空等待上限；internal 供测试缩短。 */
    internal var drainTimeoutMs = 5_000L

    @Volatile
    private var started = false

    /** 写入端可替换（测试用）；生产默认写库。时间为事件发生时刻。 */
    internal var loginWriter: suspend (String, Int?, String, String?, Boolean, String?, LocalDateTime) -> Unit =
        { username, userId, ip, userAgent, success, errorCode, occurredAt ->
            AuditRepository.insertLogin(username, userId, ip, userAgent, success, errorCode, occurredAt)
        }

    internal var apiCallWriter: suspend (ApiCallRecordInput) -> Unit = { input ->
        AuditRepository.insertApiCall(
            input.userId,
            input.username,
            input.method,
            input.path,
            input.queryString,
            input.statusCode,
            input.durationMs,
            input.ip,
            input.userAgent,
            input.occurredAt,
        )
    }

    /** 因积压或停机被丢弃的记录数；internal 供测试与监控断言。 */
    internal fun droppedCount(): Long = dropped.get()

    fun recordLogin(
        username: String,
        userId: Int?,
        ip: String,
        userAgent: String?,
        success: Boolean,
        errorCode: String?,
    ) {
        // 事件时间在入队前捕获：积压不会把发生时间推迟到写库时刻
        val occurredAt = LocalDateTime.now()
        enqueue {
            loginWriter(username, userId, ip, userAgent, success, errorCode, occurredAt)
        }
    }

    fun recordApiCall(input: ApiCallRecordInput) = enqueue {
        apiCallWriter(input)
    }

    private fun enqueue(task: suspend () -> Unit) {
        ensureStarted()
        // 队列满或停机关闭后入队失败，都计入丢弃
        if (queue.trySend(task).isFailure) {
            val total = dropped.incrementAndGet()
            if (total == 1L || total % 1_000L == 0L) {
                log.warn("审计写入积压，新记录开始丢弃，累计 {} 条", total)
            }
        }
    }

    private fun ensureStarted() {
        if (started) return
        synchronized(lifecycleLock) {
            if (started) return
            repeat(WORKERS) {
                workers += scope.launch {
                    for (task in queue) {
                        inFlight.incrementAndGet()
                        try {
                            runCatching { task() }.onFailure { log.warn("写入审计记录失败", it) }
                        } finally {
                            inFlight.decrementAndGet()
                        }
                    }
                }
            }
            started = true
        }
    }

    /** 停机：关闭入队，限时等待消费者排空；超时取消消费者并统计未写出的损失。 */
    fun shutdown() {
        queue.close()
        val drained = runBlocking {
            withTimeoutOrNull(drainTimeoutMs) {
                workers.forEach { it.join() }
            } != null
        }
        if (!drained) {
            scope.cancel()
            // 队列剩余 + 已取出但未写完的被取消记录；两个集合不相交，不会重复计数
            var lost = inFlight.get()
            while (queue.tryReceive().isSuccess) lost++
            if (lost > 0) {
                log.warn("审计停机等待超时，丢弃 {} 条未写出记录（累计 {} 条）", lost, dropped.addAndGet(lost))
            }
        }
    }

    /** 重建生命周期；internal 供测试隔离（生产只在停机时调用一次 shutdown）。 */
    internal fun resetForTest() {
        synchronized(lifecycleLock) {
            if (!queue.isClosedForSend) queue.close()
            scope.cancel()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            queue = Channel(QUEUE_CAPACITY)
            workers.clear()
            dropped.set(0)
            inFlight.set(0)
            started = false
        }
    }

    suspend fun listLogins(limit: Int, offset: Long, username: String?, success: Boolean?) =
        AuditRepository.listLogins(limit, offset, username, success)

    suspend fun listApiCalls(limit: Int, offset: Long, username: String?, method: String?, path: String?) =
        AuditRepository.listApiCalls(limit, offset, username, method, path)
}
