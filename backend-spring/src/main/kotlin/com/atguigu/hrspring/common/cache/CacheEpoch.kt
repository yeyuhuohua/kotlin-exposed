package com.atguigu.hrspring.common.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/** 两个后端共享失效令牌，缓存值仍使用各自的命名空间与序列化。 */
@Component
class CacheEpoch(redis: StringRedisTemplate) {
    private val state = EpochState(
        read = {
            val data = redis.opsForValue().get(DATA_KEY).orEmpty()
            val auth = redis.opsForValue().get(AUTH_KEY).orEmpty()
            "$data:$auth"
        },
        rotate = { redis.opsForValue().set(DATA_KEY, UUID.randomUUID().toString()) },
    )

    fun current(): String? = state.current()

    fun invalidate() = state.invalidate()

    fun unavailable() = state.unavailable()

    companion object {
        const val DATA_KEY = "hr:cache:epoch"
        const val AUTH_KEY = "hr:auth:invalidate:epoch"
    }
}

/** 启动和故障恢复先换代，失效失败期间旁路，退避结束前不重试 Redis。 */
internal class EpochState(
    private val read: () -> String,
    private val rotate: () -> Unit,
    private val clock: () -> Long = System::nanoTime,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var dirty = true
    private var retryAt = 0L

    @Synchronized
    fun current(): String? {
        if (clock() < retryAt) return null
        return try {
            if (dirty) {
                rotate()
                dirty = false
            }
            read()
        } catch (e: RuntimeException) {
            unavailable()
            log.warn("Redis version unavailable; cache bypassed", e)
            null
        }
    }

    @Synchronized
    fun invalidate() {
        dirty = true
        current()
    }

    @Synchronized
    fun unavailable() {
        dirty = true
        retryAt = clock() + 5_000_000_000L
    }
}

/** 缺失回填令牌时禁止写缓存；异常退出的读取由请求过滤器清理。 */
internal object CacheLoads {
    private val snapshots = ThreadLocal.withInitial { mutableMapOf<Pair<String, Any>, String>() }

    fun remember(name: String, key: Any, epoch: String) {
        val entries = snapshots.get()
        if (entries.size >= 256) entries.clear()
        entries[name to key] = epoch
    }

    fun take(name: String, key: Any): String? = snapshots.get().remove(name to key)

    fun clear() = snapshots.remove()
}
