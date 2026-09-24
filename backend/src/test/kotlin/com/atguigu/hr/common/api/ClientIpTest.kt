package com.atguigu.hr.common.api

import kotlin.test.Test
import kotlin.test.assertEquals

/** 只有可信代理传来的 X-Forwarded-For 才被采用，且从右往左跳过可信跳。 */
class ClientIpTest {
    private val trusted = setOf("127.0.0.1", "::1", "10.0.0.1")

    @Test
    fun `直连不可信时忽略转发头`() {
        assertEquals(
            "203.0.113.9",
            resolveClientIp(direct = "203.0.113.9", forwardedFor = "1.1.1.1", trusted = trusted),
            "攻击者直连后端伪造 X-Forwarded-For 不能生效",
        )
    }

    @Test
    fun `可信代理场景取第一个不可信跳`() {
        assertEquals(
            "198.51.100.7",
            resolveClientIp(
                direct = "127.0.0.1",
                forwardedFor = "198.51.100.7, 10.0.0.1",
                trusted = trusted,
            ),
        )
    }

    @Test
    fun `调用方伪造的左侧条目在代理追加后不会被采用`() {
        // 调用方自带 XFF: 1.1.1.1，代理追加真实来源后变为 "1.1.1.1, 198.51.100.7"
        assertEquals(
            "198.51.100.7",
            resolveClientIp(direct = "127.0.0.1", forwardedFor = "1.1.1.1, 198.51.100.7", trusted = trusted),
            "应取代理追加的真实来源，而不是调用方自报的地址",
        )
    }

    @Test
    fun `没有转发头时用直连地址`() {
        assertEquals("203.0.113.9", resolveClientIp("203.0.113.9", null, trusted))
        assertEquals("203.0.113.9", resolveClientIp("203.0.113.9", "  ", trusted))
    }

    @Test
    fun `整条转发链都可信时回落到直连地址`() {
        assertEquals(
            "127.0.0.1",
            resolveClientIp(direct = "127.0.0.1", forwardedFor = "10.0.0.1, ::1", trusted = trusted),
        )
    }

    @Test
    fun `主机名与 IPv6 全形会归一后再比较`() {
        // Netty 对回环连接可能返回 localhost 或 0:0:0:0:0:0:0:1，必须与本机白名单匹配
        assertEquals(
            "198.51.100.7",
            resolveClientIp(direct = "localhost", forwardedFor = "198.51.100.7", trusted = setOf("127.0.0.1")),
        )
        assertEquals(
            "198.51.100.7",
            resolveClientIp(direct = "[0:0:0:0:0:0:0:1]", forwardedFor = "198.51.100.7", trusted = setOf("::1")),
        )
        assertEquals(
            "198.51.100.7",
            resolveClientIp(direct = "127.0.0.1", forwardedFor = "198.51.100.7", trusted = setOf("LOCALHOST")),
        )
    }

    @Test
    fun `IPv6 压缩形式与展开形式按地址值相等`() {
        // Netty 返回展开形式 2001:db8:0:0:0:0:0:10，配置里写压缩形式 2001:db8::10 也必须匹配
        assertEquals(
            "198.51.100.7",
            resolveClientIp(
                direct = "2001:db8:0:0:0:0:0:10",
                forwardedFor = "198.51.100.7",
                trusted = setOf("2001:db8::10"),
            ),
        )
        assertEquals(
            "2001:db8:0:0:0:0:0:10",
            resolveClientIp(
                direct = "2001:db8:0:0:0:0:0:10",
                forwardedFor = "198.51.100.7",
                trusted = setOf("2001:db8::99"),
            ),
            "地址不同的 IPv6 代理不可信",
        )
    }
}
