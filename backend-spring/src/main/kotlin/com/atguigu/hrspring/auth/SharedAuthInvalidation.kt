package com.atguigu.hrspring.auth

import com.atguigu.hrspring.common.cache.CacheEpoch
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** 与 Ktor 共用鉴权撤销计数；失败期间保留广播，按短退避重试。 */
@Component
class SharedAuthInvalidation(private val redis: StringRedisTemplate) {
    private var seen: String? = null
    private var pending = false
    private var retryAt = 0L

    @Synchronized
    fun advanced(): Boolean {
        if (System.nanoTime() < retryAt) return false
        return try {
            if (pending) {
                redis.opsForValue().increment(CacheEpoch.AUTH_KEY)
                pending = false
            }
            val current = redis.opsForValue().get(CacheEpoch.AUTH_KEY).orEmpty()
            val previous = seen
            seen = current
            current != previous
        } catch (e: RuntimeException) {
            retryAt = System.nanoTime() + 5_000_000_000L
            false
        }
    }

    @Synchronized
    fun broadcast() {
        pending = true
        if (System.nanoTime() < retryAt) return
        try {
            redis.opsForValue().increment(CacheEpoch.AUTH_KEY)
            pending = false
        } catch (e: RuntimeException) {
            retryAt = System.nanoTime() + 5_000_000_000L
        }
    }
}
