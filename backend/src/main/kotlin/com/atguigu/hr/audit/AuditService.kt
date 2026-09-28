package com.atguigu.hr.audit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 审计日志的异步写入入口：有界队列 + 固定 worker 消费，写入并发有上限。
 * 数据库变慢导致积压时丢弃新记录并计数告警，绝不让审计拖垮请求处理。
 * 查询直接透传仓储（审计数据实时变化，不进 Redis 缓存）。
 * 客户端 IP 由 common/api/ClientIp 按可信代理解析，与登录限流共用同一来源。
 */
object AuditService {
    private const val QUEUE_CAPACITY = 1_000
    private const val WORKERS = 2
    private val log = LoggerFactory.getLogger(AuditService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(QUEUE_CAPACITY)
    private val dropped = AtomicLong(0)

    @Volatile
    private var started = false

    /** 写入端可替换（测试用）；生产默认写库。 */
    internal var loginWriter: suspend (String, Int?, String, String?, Boolean, String?) -> Unit =
        { username, userId, ip, userAgent, success, errorCode ->
            AuditRepository.insertLogin(username, userId, ip, userAgent, success, errorCode)
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
        )
    }

    /** 因积压被丢弃的记录数；internal 供测试与监控断言。 */
    internal fun droppedCount(): Long = dropped.get()

    fun recordLogin(
        username: String,
        userId: Int?,
        ip: String,
        userAgent: String?,
        success: Boolean,
        errorCode: String?,
    ) = enqueue {
        loginWriter(username, userId, ip, userAgent, success, errorCode)
    }

    fun recordApiCall(input: ApiCallRecordInput) = enqueue {
        apiCallWriter(input)
    }

    private fun enqueue(task: suspend () -> Unit) {
        ensureStarted()
        if (queue.trySend(task).isFailure) {
            val total = dropped.incrementAndGet()
            if (total == 1L || total % 1_000L == 0L) {
                log.warn("审计写入积压，新记录开始丢弃，累计 {} 条", total)
            }
        }
    }

    private fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            repeat(WORKERS) {
                scope.launch {
                    for (task in queue) {
                        runCatching { task() }.onFailure { log.warn("写入审计记录失败", it) }
                    }
                }
            }
            started = true
        }
    }

    suspend fun listLogins(limit: Int, offset: Long, username: String?, success: Boolean?) =
        AuditRepository.listLogins(limit, offset, username, success)

    suspend fun listApiCalls(limit: Int, offset: Long, username: String?, method: String?, path: String?) =
        AuditRepository.listApiCalls(limit, offset, username, method, path)

    fun shutdown() {
        scope.cancel()
    }
}
