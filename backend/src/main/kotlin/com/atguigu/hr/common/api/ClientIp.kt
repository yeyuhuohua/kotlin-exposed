package com.atguigu.hr.common.api

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.origin
import io.ktor.server.request.header

/**
 * 真实客户端 IP 解析：登录限流与审计记录共用。
 *
 * 只有直连地址是可信代理（`security.trustedProxies`）时才采用 X-Forwarded-For，
 * 并从右往左跳过可信跳，取第一个不可信地址；直连不可信时忽略转发头，
 * 调用方无法伪造来源。未配置时只信任本机回环（同机反向代理）。
 */
object ClientIp {
    private val DEFAULT_TRUSTED = setOf("127.0.0.1", "::1")

    @Volatile
    private var trustedProxies: Set<String> = DEFAULT_TRUSTED

    fun configure(config: ApplicationConfig) {
        trustedProxies = config.propertyOrNull("security.trustedProxies")?.getList()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.toSet()
            ?: DEFAULT_TRUSTED
    }

    fun resolve(call: ApplicationCall): String = resolveClientIp(
        direct = call.request.origin.remoteHost,
        forwardedFor = call.request.header(HttpHeaders.XForwardedFor),
        trusted = trustedProxies,
    )
}

/** X-Forwarded-For 从左到右是客户端到最近代理；从右往左跳过可信代理，第一个不可信跳即真实客户端。 */
fun resolveClientIp(direct: String, forwardedFor: String?, trusted: Set<String>): String {
    if (direct !in trusted) return direct
    val hops = forwardedFor
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: return direct
    return hops.asReversed().firstOrNull { it !in trusted } ?: direct
}

fun ApplicationCall.clientIp(): String = ClientIp.resolve(this)
