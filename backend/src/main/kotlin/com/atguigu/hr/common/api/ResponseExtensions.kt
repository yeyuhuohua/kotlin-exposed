package com.atguigu.hr.common.api

import com.atguigu.hr.common.cache.Cached
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond

suspend inline fun <reified T> ApplicationCall.respondOk(data: T, message: String = "ok") {
    respond(HttpStatusCode.OK, ApiResult.ok(data, message))
}

/** 失败响应默认按状态码填 error，调用方需要更精确的语义时再显式指定。 */
suspend fun ApplicationCall.respondFail(
    status: HttpStatusCode,
    message: String,
    error: String = ErrorCode.forStatus(status),
) {
    respond(status, ApiResult.fail(status.value, message, error))
}

/** 把缓存命中情况写到响应头，便于对照 Spring 的 cache hit。 */
internal fun ApplicationCall.cacheHeader(cached: Cached<*>) {
    response.header("X-Cache", if (cached.hit) "HIT" else "MISS")
}
