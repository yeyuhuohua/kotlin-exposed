package com.atguigu.hr.audit

import com.atguigu.hr.auth.UserPrincipal
import com.atguigu.hr.common.api.clientIp
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.request.userAgent
import io.ktor.util.AttributeKey

/** 一次 /api 调用的记录输入。 */
data class ApiCallRecordInput(
    val userId: Int?,
    val username: String?,
    val method: String,
    val path: String,
    val queryString: String?,
    val statusCode: Int,
    val durationMs: Long,
    val ip: String,
    val userAgent: String?,
)

/** 不需要记录的路径：顶栏健康检查等高频轮询噪音。 */
val AUDIT_EXCLUDED_PATHS: Set<String> = setOf("/api/health")

/** 只记录以 /api/ 开头且不在排除清单里的请求。 */
fun shouldAuditApiCall(path: String): Boolean = path.startsWith("/api/") && path !in AUDIT_EXCLUDED_PATHS

class ApiCallAuditConfig {
    /** 记录落地方式，测试可替换成同步 collector。 */
    var sink: (ApiCallRecordInput) -> Unit = { AuditService.recordApiCall(it) }
}

private val auditStartKey = AttributeKey<Long>("ApiCallAuditStartNs")

/**
 * /api 调用审计：onCall 记起始时间，响应发送后（ResponseSent，状态码已包含
 * 内容协商等转换的最终结果）记录方法、路径、状态、耗时与身份。
 * 装在 route("/api") 内，文档页与静态资源不经过它。
 */
val ApiCallAudit = createApplicationPlugin("ApiCallAudit", ::ApiCallAuditConfig) {
    val sink = pluginConfig.sink
    onCall { call ->
        if (shouldAuditApiCall(call.request.path())) {
            call.attributes.put(auditStartKey, System.nanoTime())
        }
    }
    on(ResponseSent) { call ->
        // 记录后立即移除起始标记：即使响应被重复提交（如认证 challenge 后又有人 respond），
        // 一次请求也只产生一条审计记录。
        val startedNs = call.attributes.getOrNull(auditStartKey) ?: return@on
        call.attributes.remove(auditStartKey)
        sink(call.toAuditInput(startedNs))
    }
}

internal fun ApplicationCall.toAuditInput(startedNs: Long): ApiCallRecordInput {
    val user = principal<UserPrincipal>()?.user
    return ApiCallRecordInput(
        userId = user?.id,
        username = user?.username,
        method = request.httpMethod.value,
        path = request.path(),
        queryString = request.queryString().ifBlank { null },
        statusCode = response.status()?.value ?: 0,
        durationMs = (System.nanoTime() - startedNs) / 1_000_000,
        ip = this.clientIp(),
        userAgent = request.userAgent(),
    )
}
