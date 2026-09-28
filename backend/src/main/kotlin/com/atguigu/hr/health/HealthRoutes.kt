package com.atguigu.hr.health

import com.atguigu.hr.common.api.ApiResult
import com.atguigu.hr.common.api.ErrorCode
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.responseExamples
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.healthRoutes() {
    /** MySQL 与 Redis 连通性检查。 */
    get("/health") {
        val health = HealthService.check()
        if (health.status == "UP") {
            call.respondOk(health)
        } else {
            // 保留健康详情在 data 里，error 给出稳定错误码供监控与前端区分。
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiResult(
                    code = 503,
                    message = "dependency unavailable",
                    data = health,
                    error = ErrorCode.DEPENDENCY_UNAVAILABLE,
                ),
            )
        }
    }.describe {
        summary = "MySQL 与 Redis 连通性检查"
        tag("system")
        responseExamples(
            sampleHealth,
            fails = arrayOf(HttpStatusCode.ServiceUnavailable to "dependency unavailable"),
        )
    }
}
