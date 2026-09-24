package com.atguigu.hr.common.api

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import java.net.InetAddress

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
        // remoteHost 可能是反向解析出的主机名（如 localhost），remoteAddress 才是连接对端地址
        direct = call.request.origin.remoteAddress,
        forwardedFor = call.request.header(HttpHeaders.XForwardedFor),
        trusted = trustedProxies,
    )
}

/** 统一大小写、去掉 IPv6 方括号，并把本机别名归一到规范 IP，避免主机名与 IP 互不相等。 */
fun normalizeIp(host: String): String {
    var value = host.trim().lowercase()
    if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length - 1)
    if (value == "localhost") return "127.0.0.1"
    if (':' in value) {
        // IPv6 按地址值展开成完整形式再比较：2001:db8::10 与 2001:db8:0:0:0:0:0:10 相等。
        // getByName 对合法的地址字面量不做 DNS 查询；非法输入原样返回。
        return runCatching { InetAddress.getByName(value).hostAddress }.getOrDefault(value)
    }
    return value
}

/** X-Forwarded-For 从左到右是客户端到最近代理；从右往左跳过可信代理，第一个不可信跳即真实客户端。 */
fun resolveClientIp(direct: String, forwardedFor: String?, trusted: Set<String>): String {
    val normalizedTrusted = trusted.mapTo(HashSet()) { normalizeIp(it) }
    val normalizedDirect = normalizeIp(direct)
    if (normalizedDirect !in normalizedTrusted) return normalizedDirect
    val hops = forwardedFor
        ?.split(',')
        ?.map { normalizeIp(it) }
        ?.filter { it.isNotEmpty() }
        ?: return normalizedDirect
    return hops.asReversed().firstOrNull { it !in normalizedTrusted } ?: normalizedDirect
}

fun ApplicationCall.clientIp(): String = ClientIp.resolve(this)
