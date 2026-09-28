package com.atguigu.hr.auth

import com.atguigu.hr.config.RedisFactory
import kotlinx.coroutines.future.await
import java.util.concurrent.atomic.AtomicLong

/**
 * 多实例间的鉴权失效同步：失效方对共享计数 +1（broadcast），
 * 各实例在鉴权前比对计数，计数前进就清空本地鉴权缓存（advanced），
 * 因此其它实例最迟在下一个请求时感知撤销。
 * Redis 不可用时退回单实例语义（仅本地失效，最长滞后一个本地 TTL）。
 */
class SharedInvalidation(
    private val key: String = "hr:auth:invalidate:epoch",
    private val reader: suspend (String) -> String? = { RedisFactory.async.get(it).await() },
    private val writer: suspend (String) -> Unit = { RedisFactory.async.incr(it).await() },
) {
    private val seen = AtomicLong(0)

    /** 广播一次失效；失败只降级为本实例失效，不影响主流程。 */
    suspend fun broadcast() {
        runCatching { writer(key) }
    }

    /** 共享计数较上次前进时返回 true，调用方应清空本地缓存；Redis 不可用返回 false。 */
    suspend fun advanced(): Boolean {
        val current = runCatching { reader(key)?.toLongOrNull() ?: 0L }.getOrNull() ?: return false
        if (current <= seen.get()) return false
        seen.accumulateAndGet(current, ::maxOf)
        return true
    }
}
