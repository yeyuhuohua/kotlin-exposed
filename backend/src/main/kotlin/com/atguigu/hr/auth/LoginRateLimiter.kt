package com.atguigu.hr.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * 单实例内存限流：按 key 统计滑动窗口内的失败次数，达到上限后拒绝到窗口结束。
 *
 * 只保护当前进程；多实例部署时每个实例各算一份，仍需在网关或反向代理上限流（见 AUTH.md）。
 * 时间来源可注入，便于测试。
 */
class LoginRateLimiter(
    private val maxFailures: Int,
    private val windowMillis: Long,
    private val maxTrackedKeys: Int = 10_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Bucket(var failures: Int, var expiresAt: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    init {
        require(maxFailures > 0) { "maxFailures must be positive" }
        require(windowMillis > 0) { "windowMillis must be positive" }
    }

    /** 返回还需等待的秒数；null 表示放行。 */
    fun retryAfterSeconds(key: String): Long? {
        val bucket = buckets[key] ?: return null
        val now = clock()
        if (bucket.expiresAt <= now) {
            buckets.remove(key, bucket)
            return null
        }
        if (bucket.failures < maxFailures) return null
        return (bucket.expiresAt - now + 999) / 1000
    }

    fun recordFailure(key: String) {
        val now = clock()
        val bucket = buckets.compute(key) { _, existing ->
            if (existing == null || existing.expiresAt <= now) Bucket(1, now + windowMillis)
            else existing.also { it.failures++ }
        }
        if (bucket != null && buckets.size > maxTrackedKeys) prune(now)
    }

    fun reset(key: String) {
        buckets.remove(key)
    }

    fun trackedKeys(): Int = buckets.size

    /** 先清过期项；仍超上限时按最快过期的时间丢弃，保证内存有界（被丢弃的 key 相当于放行）。 */
    private fun prune(now: Long) {
        buckets.entries.removeIf { it.value.expiresAt <= now }
        val overflow = buckets.size - maxTrackedKeys
        if (overflow <= 0) return
        buckets.entries
            .sortedBy { it.value.expiresAt }
            .take(overflow)
            .forEach { buckets.remove(it.key, it.value) }
    }
}
