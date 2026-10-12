package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import org.springframework.stereotype.Component
import java.util.ArrayDeque

/**
 * 登录失败滑动窗口限流:按账号(默认 8 次/300 秒)和按 IP(默认 30 次/300 秒)双维度。
 * 达到上限后拒绝到窗口结束;登录成功只清账号维度,IP 维度保留。
 */
@Component
class LoginThrottle(
    properties: HrProperties,
) {
    private val config = properties.auth.loginRateLimit
    private val buckets = mutableMapOf<String, ArrayDeque<Long>>()
    private val overflowAccount = ArrayDeque<Long>()
    private val overflowAddress = ArrayDeque<Long>()
    private var nextPruneAt = 0L

    internal val trackedKeyCount: Int get() = synchronized(this) { buckets.size }

    val enabled: Boolean get() = config.enabled

    fun accountKey(username: String) = "account:$username"

    fun addressKey(ip: String) = "address:$ip"

    /**
     * 任一维度被限流时返回剩余秒数(向上取整,取两者最大),否则返回 null。
     */
    @Synchronized
    fun blockedSeconds(accountKey: String, addressKey: String): Long? {
        if (!config.enabled) return null
        val now = System.currentTimeMillis()
        val accountWait = remainingBlock(accountKey, config.maxAccountFailures, now)
        val addressWait = remainingBlock(addressKey, config.maxAddressFailures, now)
        return listOfNotNull(accountWait, addressWait).maxOrNull()
    }

    @Synchronized
    fun recordFailure(accountKey: String, addressKey: String) {
        if (!config.enabled) return
        val now = System.currentTimeMillis()
        record(accountKey, config.maxAccountFailures, now)
        record(addressKey, config.maxAddressFailures, now)
    }

    @Synchronized
    fun recordSuccess(accountKey: String) {
        if (!config.enabled) return
        buckets.remove(accountKey)
    }

    private fun remainingBlock(key: String, max: Int, now: Long): Long? {
        val deque = buckets[key] ?: overflow(key)
        prune(deque, now)
        if (deque.size < max) return null
        val oldest = deque.peekFirst() ?: return null
        return (oldest + config.windowSeconds * 1000 - now + 999) / 1000
    }

    private fun record(key: String, max: Int, now: Long) {
        if (buckets.size >= MAX_TRACKED_KEYS && now >= nextPruneAt) {
            buckets.entries.removeIf { (_, deque) ->
                prune(deque, now)
                deque.isEmpty()
            }
            nextPruneAt = now + 1_000
        }
        // 容量满时保留已跟踪账号，新增键共享溢出桶，避免攻击者通过换键绕过限流。
        val deque = buckets[key] ?: if (buckets.size < MAX_TRACKED_KEYS) {
            ArrayDeque<Long>().also { buckets[key] = it }
        } else overflow(key)
        prune(deque, now)
        if (deque.size < max) deque.addLast(now)
    }

    private fun overflow(key: String): ArrayDeque<Long> =
        if (key.startsWith("address:")) overflowAddress else overflowAccount

    private fun prune(deque: ArrayDeque<Long>, now: Long) {
        val cutoff = now - config.windowSeconds * 1000
        while (true) {
            val head = deque.peekFirst() ?: break
            if (head > cutoff) break
            deque.pollFirst()
        }
    }

    companion object {
        const val MAX_TRACKED_KEYS = 10000
    }
}
