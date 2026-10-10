package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import org.springframework.stereotype.Component
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * 登录失败滑动窗口限流:按账号(默认 8 次/300 秒)和按 IP(默认 30 次/300 秒)双维度。
 * 达到上限后拒绝到窗口结束;登录成功只清账号维度,IP 维度保留。
 */
@Component
class LoginThrottle(
    properties: HrProperties,
) {
    private val config = properties.auth.loginRateLimit
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()

    val enabled: Boolean get() = config.enabled

    fun accountKey(username: String) = "account:$username"

    fun addressKey(ip: String) = "address:$ip"

    /**
     * 任一维度被限流时返回剩余秒数(向上取整,取两者最大),否则返回 null。
     */
    fun blockedSeconds(accountKey: String, addressKey: String): Long? {
        if (!config.enabled) return null
        val now = System.currentTimeMillis()
        val accountWait = remainingBlock(accountKey, config.maxAccountFailures, now)
        val addressWait = remainingBlock(addressKey, config.maxAddressFailures, now)
        return listOfNotNull(accountWait, addressWait).maxOrNull()
    }

    fun recordFailure(accountKey: String, addressKey: String) {
        if (!config.enabled) return
        record(accountKey)
        record(addressKey)
    }

    fun recordSuccess(accountKey: String) {
        if (!config.enabled) return
        buckets.remove(accountKey)
    }

    private fun remainingBlock(key: String, max: Int, now: Long): Long? {
        val deque = buckets[key] ?: return null
        synchronized(deque) {
            prune(deque, now)
            if (deque.size < max) return null
            val oldest = deque.peekFirst() ?: return null
            return (oldest + config.windowSeconds * 1000 - now + 999) / 1000
        }
    }

    private fun record(key: String) {
        val now = System.currentTimeMillis()
        val deque = buckets.computeIfAbsent(key) { ArrayDeque() }
        synchronized(deque) {
            prune(deque, now)
            deque.addLast(now)
        }
        if (buckets.size > MAX_TRACKED_KEYS) {
            val cutoff = System.currentTimeMillis()
            buckets.entries.removeIf { (_, d) ->
                synchronized(d) {
                    prune(d, cutoff)
                    d.isEmpty()
                }
            }
        }
    }

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
