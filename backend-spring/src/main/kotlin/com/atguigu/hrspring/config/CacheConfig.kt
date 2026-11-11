package com.atguigu.hrspring.config

import com.atguigu.hrspring.common.cache.CacheHitMarker
import com.atguigu.hrspring.common.cache.CacheEpoch
import com.atguigu.hrspring.common.cache.CacheLoads
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.kotlinModule
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/**
 * 业务缓存使用 Spring Cache 注解。共享版本键阻止跨实例旧回填；失效在事务提交后发布，
 * 故障期间退避并旁路，旧版本数据按 TTL 清理。
 */
@Configuration
@EnableCaching
class CacheConfig(
    private val properties: HrProperties,
    private val connectionFactory: RedisConnectionFactory,
    private val epoch: CacheEpoch,
) : CachingConfigurer {

    /** 创建带共享失效版本和故障降级能力的 Redis 缓存管理器。 */
    @Bean
    override fun cacheManager(): CacheManager {
        val config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(properties.cache.ttlSeconds))
            .computePrefixWith { name -> "hrs:$name:" }
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(cacheSerializer()),
            )
        val redis = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build()
        return RecordingCacheManager(TransactionAwareCacheManagerProxy(redis), epoch)
    }

    /** 创建缓存异常处理器，使 Redis 故障不直接破坏业务请求。 */
    @Bean
    override fun errorHandler(): CacheErrorHandler = LoggingCacheErrorHandler(epoch)
}

/** Jackson 3。Kotlin 数据类是 final，序列化器仍会给它们写类型信息。 */
internal fun cacheSerializer(): GenericJacksonJsonRedisSerializer {
    val validator = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.atguigu.hrspring.")
        .allowIfSubType("java.util.")
        .allowIfSubType("java.time.")
        .allowIfSubType("java.math.")
        .allowIfSubType(emptyList<Any>().javaClass)
        .build()
    return GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(validator)
        .customize { builder ->
            builder.addModule(kotlinModule())
        }
        .build()
}

private class LoggingCacheErrorHandler(private val epoch: CacheEpoch) : CacheErrorHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 缓存读取失败时记录故障并回退到数据源。 */
    override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
        epoch.unavailable()
        log.warn("redis cache get failed for {}: {}", cache.name, exception.message)
    }

    /** 缓存写入失败时记录故障并保留本次业务结果。 */
    override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
        epoch.unavailable()
        log.warn("redis cache put failed for {}: {}", cache.name, exception.message)
    }

    /** 缓存失效失败时记录故障，避免业务写入被回滚。 */
    override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
        epoch.unavailable()
        log.warn("redis cache evict failed for {}: {}", cache.name, exception.message)
    }

    /** 缓存清空失败时记录故障并执行降级。 */
    override fun handleCacheClearError(exception: RuntimeException, cache: Cache) {
        epoch.unavailable()
        log.warn("redis cache clear failed for {}: {}", cache.name, exception.message)
    }
}

private class RecordingCacheManager(
    private val delegate: CacheManager,
    private val epoch: CacheEpoch,
) : CacheManager {
    private val caches = ConcurrentHashMap<String, Cache>()

    /** 按名称取得带失效版本包装的缓存实例。 */
    override fun getCache(name: String): Cache? = caches[name] ?: delegate.getCache(name)?.let {
        caches.computeIfAbsent(name) { _ -> RecordingCache(it, epoch::current, epoch::invalidate) }
    }

    /** 返回当前缓存管理器支持的缓存名称。 */
    override fun getCacheNames(): Collection<String> = delegate.cacheNames
}

internal class RecordingCache(
    private val delegate: Cache,
    private val currentEpoch: () -> String?,
    private val invalidateEpoch: () -> Unit,
) : Cache {
    /** 返回当前缓存的名称。 */
    override fun getName(): String = delegate.name

    /** 返回底层 Redis 缓存实例。 */
    override fun getNativeCache(): Any = delegate.nativeCache

    /** 按缓存键读取值，并在返回前复核失效版本。 */
    override fun get(key: Any): Cache.ValueWrapper? {
        CacheLoads.take(name, key)
        val version = currentEpoch()
        // GET 等待期间可能已发生失效，返回前必须再次核对版本。
        val value = if (version == null) null else delegate.get("$version:$key")
            ?.takeIf { currentEpoch() == version }
        // 未命中时保留读取版本，回填只能写入同一版本，避免复活旧数据。
        if (version != null && value == null) CacheLoads.remember(name, key, version)
        CacheHitMarker.markFirst(value != null)
        return value
    }

    /** 读取缓存值并校验目标类型。 */
    override fun <T : Any> get(key: Any, type: Class<T>?): T? {
        val value = get(key)?.get() ?: return null
        require(type == null || type.isInstance(value)) { "Cached value has an incompatible type" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    /** 读取缓存；未命中时调用加载器，并按版本规则回填。 */
    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
        get(key)?.get()?.let {
            @Suppress("UNCHECKED_CAST")
            return it as T
        }
        return try {
            valueLoader.call().also { put(key, it) }
        } finally {
            CacheLoads.take(name, key)
        }
    }

    /** 使用读取时的失效版本写入缓存，版本缺失或变化时跳过。 */
    override fun put(key: Any, value: Any?) {
        val version = CacheLoads.take(name, key) ?: return
        if (currentEpoch() != version || value == null) return
        // 事务提交后仍写入读取时的版本键，失效后的新请求不会读到这份旧回填。
        delegate.put("$version:$key", value)
    }

    /** 使指定缓存项失效；缓存不可用时按旁路策略处理。 */
    override fun evict(key: Any) {
        clear()
    }

    /** 仅当缓存项存在时使其失效。 */
    override fun evictIfPresent(key: Any): Boolean = invalidate()

    /** 清空当前缓存；事务同步期间推迟到提交后失效。 */
    override fun clear() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                object : org.springframework.transaction.support.TransactionSynchronization {
                    /** 把缓存失效推迟到数据库事务成功提交后执行。 */
                    override fun afterCommit() = invalidateEpoch()
                },
            )
        } else invalidateEpoch()
    }

    /** 失效当前缓存的全部条目，并返回已处理标记。 */
    override fun invalidate(): Boolean {
        clear()
        return true
    }
}

@Component
class CacheHitCleanupFilter : OncePerRequestFilter() {
    /** 请求结束后清理缓存命中标记和读取版本快照。 */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            filterChain.doFilter(request, response)
        } finally {
            CacheHitMarker.clear()
            CacheLoads.clear()
        }
    }
}
