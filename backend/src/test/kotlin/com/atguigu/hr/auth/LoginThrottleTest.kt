package com.atguigu.hr.auth

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 账号维度与地址维度必须同时生效，否则换个用户名就能绕过限流。 */
class LoginThrottleTest {
    private var now = 1_000_000L

    private fun throttle(accountLimit: Int = 2, addressLimit: Int = 3, enabled: Boolean = true) = LoginThrottle(
        enabled = enabled,
        addresses = LoginRateLimiter(addressLimit, 60_000) { now },
        accounts = LoginRateLimiter(accountLimit, 60_000) { now },
    )

    @Test
    fun `账号维度连续失败后拒绝`() {
        val throttle = throttle()
        throttle.recordFailure("10.0.0.1", "admin")
        assertNull(throttle.blockedSeconds("10.0.0.9", "admin"))
        throttle.recordFailure("10.0.0.1", "admin")
        assertEquals(60, throttle.blockedSeconds("10.0.0.9", "admin"))
    }

    @Test
    fun `用户名归一化后大小写与空白视为同一账号`() {
        val throttle = throttle()
        throttle.recordFailure("10.0.0.1", " Admin ")
        throttle.recordFailure("10.0.0.1", "admin")
        assertEquals(60, throttle.blockedSeconds("10.0.0.1", "ADMIN"))
    }

    @Test
    fun `地址维度挡住换用户名的撞库`() {
        val throttle = throttle()
        throttle.recordFailure("10.0.0.1", "a")
        throttle.recordFailure("10.0.0.1", "b")
        assertNull(throttle.blockedSeconds("10.0.0.1", "c"))
        throttle.recordFailure("10.0.0.1", "c")
        assertEquals(60, throttle.blockedSeconds("10.0.0.1", "d"))
        assertNull(throttle.blockedSeconds("10.0.0.2", "d"))
    }

    @Test
    fun `登录成功只清账号维度，地址维度保留`() {
        val throttle = throttle()
        throttle.recordFailure("10.0.0.1", "a")
        throttle.recordFailure("10.0.0.1", "a")
        throttle.recordSuccess("a")
        assertNull(throttle.blockedSeconds("10.0.0.1", "a"))
        // 地址维度未被清零，继续失败会先撞到地址上限
        throttle.recordFailure("10.0.0.1", "b")
        assertEquals(60, throttle.blockedSeconds("10.0.0.1", "c"))
    }

    @Test
    fun `关闭限流时不计数也不拒绝`() {
        val throttle = throttle(enabled = false)
        repeat(10) { throttle.recordFailure("10.0.0.1", "admin") }
        assertNull(throttle.blockedSeconds("10.0.0.1", "admin"))
        throttle.recordSuccess("admin")
    }

    @Test
    fun `拒绝响应使用 429 与稳定错误码`() {
        val rejection = throttle().rejection()
        assertEquals(HttpStatusCode.TooManyRequests, rejection.status)
        assertEquals("rate_limited", rejection.error)
        assertTrue(rejection.message.isNotBlank())
    }
}
