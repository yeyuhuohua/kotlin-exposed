package com.atguigu.hr.audit

import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.responseExamples
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

/** 审计日志查询接口：两张表都只读，权限目录里登记为仅 ADMIN。 */
@OptIn(ExperimentalKtorApi::class)
fun Route.auditRoutes() {
    /** 分页查询登录记录，支持用户名与结果过滤。 */
    get("/audit/logins") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val username = call.request.queryParameters["username"]
        val success = call.request.queryParameters["success"]?.toBooleanStrictOrNull()
        call.respondOk(AuditService.listLogins(limit, offset, username, success))
    }.describe {
        summary = "分页查询登录记录（仅 ADMIN）"
        tag("audit")
        parameters {
            query("limit") { description = "每页条数，默认 50，最大 200" }
            query("offset") { description = "跳过条数，默认 0" }
            query("username") { description = "按用户名精确过滤，例如 admin" }
            query("success") { description = "按结果过滤：true=仅成功，false=仅失败" }
        }
        responseExamples(ApiList(total = 2, items = listOf(sampleLoginRecord, sampleLoginRecordFailed)))
    }

    /** 分页查询接口调用记录，支持用户名、方法与路径前缀过滤。 */
    get("/audit/api-calls") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val username = call.request.queryParameters["username"]
        val method = call.request.queryParameters["method"]
        val path = call.request.queryParameters["path"]
        call.respondOk(AuditService.listApiCalls(limit, offset, username, method, path))
    }.describe {
        summary = "分页查询接口调用记录（仅 ADMIN）"
        tag("audit")
        parameters {
            query("limit") { description = "每页条数，默认 50，最大 200" }
            query("offset") { description = "跳过条数，默认 0" }
            query("username") { description = "按用户名精确过滤" }
            query("method") { description = "按 HTTP 方法过滤，例如 GET" }
            query("path") { description = "按路径前缀过滤，例如 /api/employees" }
        }
        responseExamples(ApiList(total = 1, items = listOf(sampleApiCallRecord)))
    }
}
