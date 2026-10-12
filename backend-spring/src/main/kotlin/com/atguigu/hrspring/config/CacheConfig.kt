package com.atguigu.hrspring.config

import com.atguigu.hrspring.common.cache.CacheHitMarker
import com.atguigu.hrspring.common.cache.CacheEpoch
import com.atguigu.hrspring.common.cache.CacheLoads
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
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

    @Bean
    override fun errorHandler(): CacheErrorHandler = LoggingCacheErrorHandler(epoch)
}

internal fun cacheSerializer(): GenericJackson2JsonRedisSerializer {
    val validator = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("com.atguigu.hrspring.")
        .allowIfSubType("java.util.")
        .allowIfSubType("java.time.")
        .allowIfSubType("java.math.")
        .allowIfSubType(emptyList<Any>().javaClass)
        .build()
    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    mapper.activateDefaultTyping(
        validator,
        ObjectMapper.DefaultTyping.EVERYTHING,
        JsonTypeInfo.As.PROPERTY,
    )
    return GenericJackson2JsonRedisSerializer(mapper)
}

private class LoggingCacheErrorHandler(private val epoch: CacheEpoch) : CacheErrorHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) {
        epoch.unavailable()
        log.warn("redis cache get failed for {}: {}", cache.name, exception.message)
    }

    override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) {
        epoch.unavailable()
        log.warn("redis cache put failed for {}: {}", cache.name, exception.message)
    }

    override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) {
        epoch.unavailable()
        log.warn("redis cache evict failed for {}: {}", cache.name, exception.message)
    }

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

    override fun getCache(name: String): Cache? = caches[name] ?: delegate.getCache(name)?.let {
        caches.computeIfAbsent(name) { _ -> RecordingCache(it, epoch::current, epoch::invalidate) }
    }

    override fun getCacheNames(): Collection<String> = delegate.cacheNames
}

internal class RecordingCache(
    private val delegate: Cache,
    private val currentEpoch: () -> String?,
    private val invalidateEpoch: () -> Unit,
) : Cache {
    override fun getName(): String = delegate.name

    override fun getNativeCache(): Any = delegate.nativeCache

    override fun get(key: Any): Cache.ValueWrapper? {
        CacheLoads.take(name, key)
        val version = currentEpoch()
        val value = if (version == null) null else delegate.get("$version:$key")
            ?.takeIf { currentEpoch() == version }
        if (version != null && value == null) CacheLoads.remember(name, key, version)
        CacheHitMarker.markFirst(value != null)
        return value
    }

    override fun <T : Any> get(key: Any, type: Class<T>?): T? {
        val value = get(key)?.get() ?: return null
        require(type == null || type.isInstance(value)) { "Cached value has an incompatible type" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

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

    override fun put(key: Any, value: Any?) {
        val version = CacheLoads.take(name, key) ?: return
        if (currentEpoch() != version || value == null) return
        // 事务提交后仍写入读取时的版本键，失效后的新请求不会读到这份旧回填。
        delegate.put("$version:$key", value)
    }

    override fun evict(key: Any) {
        clear()
    }

    override fun evictIfPresent(key: Any): Boolean = invalidate()

    override fun clear() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                object : org.springframework.transaction.support.TransactionSynchronization {
                    override fun afterCommit() = invalidateEpoch()
                },
            )
        } else invalidateEpoch()
    }

    override fun invalidate(): Boolean {
        clear()
        return true
    }
}

@Component
class CacheHitCleanupFilter : OncePerRequestFilter() {
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
