package com.atguigu.hr.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 限流依赖时间，注入时钟后才能稳定验证窗口边界。 */
class LoginRateLimiterTest {
    private var now = 1_000_000L

    private fun limiter(maxFailures: Int = 3, windowMillis: Long = 60_000, maxTrackedKeys: Int = 100) =
        LoginRateLimiter(maxFailures, windowMillis, maxTrackedKeys) { now }

    @Test
    fun `未达上限时放行`() {
        val limiter = limiter()
        limiter.recordFailure("a")
        limiter.recordFailure("a")
        assertNull(limiter.retryAfterSeconds("a"))
    }

    @Test
    fun `达到上限后拒绝并给出剩余等待秒数`() {
        val limiter = limiter()
        repeat(3) { limiter.recordFailure("a") }
        assertEquals(60, limiter.retryAfterSeconds("a"))
        now += 30_000
        assertEquals(30, limiter.retryAfterSeconds("a"))
    }

    @Test
    fun `窗口结束后计数自动失效`() {
        val limiter = limiter()
        repeat(3) { limiter.recordFailure("a") }
        now += 60_001
        assertNull(limiter.retryAfterSeconds("a"))
    }

    @Test
    fun `不同 key 互不影响且 reset 立即放行`() {
        val limiter = limiter()
        repeat(3) { limiter.recordFailure("a") }
        assertNull(limiter.retryAfterSeconds("b"))
        limiter.reset("a")
        assertNull(limiter.retryAfterSeconds("a"))
    }

    @Test
    fun `窗口过期后重新计数而非继续累加`() {
        val limiter = limiter()
        repeat(3) { limiter.recordFailure("a") }
        now += 60_001
        limiter.recordFailure("a")
        assertNull(limiter.retryAfterSeconds("a"))
    }

    @Test
    fun `追踪的 key 数量有上限，超限时丢弃最快过期的项`() {
        val limiter = limiter(maxTrackedKeys = 10)
        repeat(50) { index ->
            now += 1
            limiter.recordFailure("key-$index")
        }
        assertTrue(limiter.trackedKeys() <= 10, "未按上限裁剪：${limiter.trackedKeys()}")
        // 保留下来的是最近失败、最晚过期的 key
        assertTrue(limiter.retryAfterSeconds("key-49") == null, "最新 key 不应被误判为超限")
    }

    @Test
    fun `超过上限时先清理已过期的 key`() {
        val limiter = limiter(maxTrackedKeys = 5)
        repeat(5) { index -> limiter.recordFailure("old-$index") }
        now += 60_001
        repeat(5) { index -> limiter.recordFailure("new-$index") }
        assertEquals(5, limiter.trackedKeys())
    }
}
