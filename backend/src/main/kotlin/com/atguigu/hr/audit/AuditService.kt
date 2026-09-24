package com.atguigu.hr.audit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * 审计日志的异步写入入口：fire-and-forget，写入失败只记日志，绝不影响请求。
 * 查询直接透传仓储（审计数据实时变化，不进 Redis 缓存）。
 * 客户端 IP 由 common/api/ClientIp 按可信代理解析，与登录限流共用同一来源。
 */
object AuditService {
    private val log = LoggerFactory.getLogger(AuditService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recordLogin(
        username: String,
        userId: Int?,
        ip: String,
        userAgent: String?,
        success: Boolean,
        errorCode: String?,
    ) {
        scope.launch {
            runCatching {
                AuditRepository.insertLogin(username, userId, ip, userAgent, success, errorCode)
            }.onFailure { log.warn("写入登录记录失败", it) }
        }
    }

    fun recordApiCall(input: ApiCallRecordInput) {
        scope.launch {
            runCatching {
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
            }.onFailure { log.warn("写入接口调用记录失败", it) }
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
