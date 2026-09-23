package com.atguigu.hr.audit

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * 审计日志的异步写入入口：fire-and-forget，写入失败只记日志，绝不影响请求。
 * 查询直接透传仓储（审计数据实时变化，不进 Redis 缓存）。
 */
object AuditService {
    private val log = LoggerFactory.getLogger(AuditService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 反向代理场景取 X-Forwarded-For 首跳，否则用直连地址；不全局改 origin 语义。 */
    fun clientIp(call: ApplicationCall): String =
        call.request.header(HttpHeaders.XForwardedFor)
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: call.request.origin.remoteHost

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
