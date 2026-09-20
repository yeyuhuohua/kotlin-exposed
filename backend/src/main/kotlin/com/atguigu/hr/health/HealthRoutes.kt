package com.atguigu.hr.health

import com.atguigu.hr.common.api.ApiResult
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
    /**
     * MySQL 与 Redis 连通性检查。
     *
     * Tag: system
     * Response: 200 application/json [HealthDto] 均可用
     * Response: 503 application/json [HealthDto] 有依赖不可用
     */
    get("/health") {
        val health = HealthService.check()
        if (health.status == "UP") {
            call.respondOk(health)
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ApiResult(code = 503, message = "dependency unavailable", data = health),
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
