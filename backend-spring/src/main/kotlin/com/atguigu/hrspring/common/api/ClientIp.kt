package com.atguigu.hrspring.common.api

import com.atguigu.hrspring.config.HrProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.net.Inet6Address
import java.net.InetAddress

/**
 * 真实客户端 IP 解析:直连地址不可信时完全忽略 X-Forwarded-For;
 * 直连可信时从右往左跳过可信跳,第一个不可信地址即真实客户端。
 * 未配置可信代理时默认只信本机回环。
 */
@Component
class ClientIp(
    properties: HrProperties,
) {
    private val trusted: Set<String> = properties.security.trustedProxies
        .mapNotNull { normalize(it) }
        .ifEmpty { listOf("127.0.0.1", "0:0:0:0:0:0:0:1") }
        .toSet()

    fun resolve(request: HttpServletRequest): String {
        val direct = normalize(request.remoteAddr) ?: return request.remoteAddr ?: "unknown"
        if (direct !in trusted) return direct
        val header = request.getHeader("X-Forwarded-For") ?: return direct
        val hops = header.split(',').mapNotNull { normalize(it.trim()) }
        for (i in hops.indices.reversed()) {
            if (hops[i] !in trusted) return hops[i]
        }
        return direct
    }

    companion object {
        /** 小写、去 IPv6 方括号、localhost 归一、IPv6 展开为完整形式便于按值比较。 */
        fun normalize(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var v = raw.trim().lowercase()
            if (v.startsWith("[") && v.endsWith("]")) v = v.substring(1, v.length - 1)
            if (v == "localhost") return "127.0.0.1"
            if (v.contains(':')) {
                try {
                    val addr = InetAddress.getByName(v)
                    if (addr is Inet6Address) {
                        return addr.hostAddress?.let { expandIpv6(it) } ?: v
                    }
                } catch (_: Exception) {
                    return v
                }
            }
            return v
        }

        private fun expandIpv6(addr: String): String {
            return try {
                val bytes = InetAddress.getByName(addr).address
                if (bytes.size != 16) return addr
                buildString {
                    for (i in bytes.indices step 2) {
                        if (i > 0) append(':')
                        append(((bytes[i].toInt() and 0xff) shl 8 or (bytes[i + 1].toInt() and 0xff)).toString(16))
                    }
                }
            } catch (_: Exception) {
                addr
            }
        }
    }
}
