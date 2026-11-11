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
            val values = redis.opsForValue().multiGet(listOf(DATA_KEY, AUTH_KEY)).orEmpty()
            val data = values.getOrNull(0).orEmpty()
            val auth = values.getOrNull(1).orEmpty()
            "$data:$auth"
        },
        rotate = { redis.opsForValue().set(DATA_KEY, UUID.randomUUID().toString()) },
    )

    /** 读取当前缓存失效版本；故障退避期间返回 null。 */
    fun current(): String? = state.current()

    /** 轮换共享缓存失效版本，使旧缓存项不再被读取。 */
    fun invalidate() = state.invalidate()

    /** 标记缓存需要换代，并设置短暂故障退避。 */
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

    /** 读取当前缓存失效版本；故障退避期间返回 null。 */
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

    /** 轮换共享缓存失效版本，使旧缓存项不再被读取。 */
    @Synchronized
    fun invalidate() {
        dirty = true
        current()
    }

    /** 标记缓存需要换代，并设置短暂故障退避。 */
    @Synchronized
    fun unavailable() {
        dirty = true
        retryAt = clock() + 5_000_000_000L
    }
}

/** 缺失回填令牌时禁止写缓存；异常退出的读取由请求过滤器清理。 */
internal object CacheLoads {
    private val snapshots = ThreadLocal.withInitial { mutableMapOf<Pair<String, Any>, String>() }

    /** 在当前请求中保存缓存读取时的失效版本快照。 */
    fun remember(name: String, key: Any, epoch: String) {
        val entries = snapshots.get()
        if (entries.size >= 256) entries.clear()
        entries[name to key] = epoch
    }

    /** 取出并移除当前请求中指定缓存项的版本快照。 */
    fun take(name: String, key: Any): String? = snapshots.get().remove(name to key)

    /** 移除当前线程保存的全部缓存读取版本快照。 */
    fun clear() = snapshots.remove()
}
