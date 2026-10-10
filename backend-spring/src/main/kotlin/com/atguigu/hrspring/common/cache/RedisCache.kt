package com.atguigu.hrspring.common.cache

import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.common.api.ApiList
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis 缓存:统一 TTL(默认 600 秒);Redis 故障时对应前缀退避 5 秒,直接回源,不拖慢请求。
 * 返回 Pair(value, hit),命中情况由控制器写到 X-Cache 响应头。
 */
@Component
class RedisCache(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    properties: HrProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ttlSeconds = properties.cache.ttlSeconds
    private val failNotBefore = ConcurrentHashMap<String, Long>()
    private val generations = ConcurrentHashMap<String, AtomicLong>()
    private val pendingKeys = ConcurrentHashMap.newKeySet<String>()
    private val pendingPrefixes = ConcurrentHashMap.newKeySet<String>()

    fun <T> withCache(key: String, type: Class<T>, loader: () -> T): Pair<T, Boolean> =
        load(key, objectMapper.typeFactory.constructType(type), loader)

    fun <T> withCacheList(key: String, elementType: Class<T>, loader: () -> List<T>): Pair<List<T>, Boolean> =
        load(key, objectMapper.typeFactory.constructCollectionType(List::class.java, elementType), loader)

    /** 分页结果按元素类型反序列化，避免 ApiList 的泛型在缓存里被擦掉。 */
    fun <T> withPage(key: String, elementType: Class<T>, loader: () -> ApiList<T>): Pair<ApiList<T>, Boolean> =
        load(key, objectMapper.typeFactory.constructParametricType(ApiList::class.java, elementType), loader)

    private fun <T> load(key: String, javaType: com.fasterxml.jackson.databind.JavaType, loader: () -> T): Pair<T, Boolean> {
        flushPending()
        val prefix = prefixOf(key)
        if (isDirty(prefix, key)) return loader() to false
        val seen = generationOf(prefix)
        val cached = readRaw(key)?.let { decode<T>(it, javaType, seen) }
        if (cached != null && generationOf(prefix) == seen && !isDirty(prefix, key)) return cached to true
        val value = loader()
        writeIfCurrent(key, value, seen)
        return value to false
    }

    private fun prefixOf(key: String): String {
        val idx = key.lastIndexOf(':')
        return if (idx > 0) key.substring(0, idx + 1) else key
    }

    private fun inBackoff(prefix: String): Boolean =
        (failNotBefore[prefix] ?: 0) > System.currentTimeMillis()

    private fun backoff(prefix: String) {
        failNotBefore[prefix] = System.currentTimeMillis() + BACKOFF_MILLIS
    }

    private fun readRaw(key: String): String? {
        val prefix = prefixOf(key)
        if (inBackoff(prefix)) return null
        return try {
            redis.opsForValue().get(key)
        } catch (e: Exception) {
            log.warn("redis read failed for {}: {}", key, e.message)
            backoff(prefix)
            null
        }
    }

    private fun <T> writeIfCurrent(key: String, value: T, seen: Long) {
        val prefix = prefixOf(key)
        if (inBackoff(prefix) || generationOf(prefix) != seen || isDirty(prefix, key)) return
        val envelope = objectMapper.createObjectNode()
        envelope.put("generation", seen)
        envelope.set<JsonNode>("body", objectMapper.valueToTree(value))
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(envelope), ttlSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.warn("redis write failed for {}: {}", key, e.message)
            backoff(prefix)
        }
    }

    private fun <T> decode(raw: String, javaType: com.fasterxml.jackson.databind.JavaType, seen: Long): T? {
        val node = runCatching { objectMapper.readTree(raw) }.getOrNull() ?: return null
        val generation = node.get("generation")?.asLong() ?: return null
        if (generation != seen) return null
        val body = node.get("body") ?: return null
        return runCatching { objectMapper.convertValue<T>(body, javaType) }.getOrNull()
    }

    /** 事务提交后再删缓存。回滚时保留旧缓存，避免并发查询把未提交的旧行写回去。 */
    fun invalidateAfterCommit(keys: Collection<String>, prefixes: Collection<String> = emptyList()) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidate(keys, prefixes)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                invalidate(keys, prefixes)
            }
        })
    }

    /** 写操作完成后失效:显式 key DEL + 前缀分组 SCAN 清理;异常只降级不抛出。 */
    fun invalidate(keys: Collection<String>, prefixes: Collection<String> = emptyList()) {
        for (key in keys.distinct()) {
            bump(prefixOf(key))
            try {
                redis.delete(key)
                pendingKeys.remove(key)
            } catch (e: Exception) {
                log.warn("redis delete failed for {}: {}", key, e.message)
                pendingKeys.add(key)
                backoff(prefixOf(key))
            }
        }
        for (prefix in prefixes.distinct()) {
            bump(prefix)
            if (!invalidateByPrefix(prefix)) {
                pendingPrefixes.add(prefix)
            } else {
                pendingPrefixes.remove(prefix)
            }
        }
    }

    private fun generationOf(prefix: String): Long =
        generations.computeIfAbsent(prefix) { AtomicLong(0) }.get()

    private fun bump(prefix: String) {
        generations.computeIfAbsent(prefix) { AtomicLong(0) }.incrementAndGet()
    }

    private fun isDirty(prefix: String, key: String): Boolean =
        pendingPrefixes.contains(prefix) || pendingKeys.contains(key)

    /** Redis 恢复后把上次没删掉的 key 再清一次，清掉之前不把旧值当命中。 */
    private fun flushPending() {
        for (key in pendingKeys.toList()) {
            try {
                redis.delete(key)
                pendingKeys.remove(key)
                failNotBefore.remove(prefixOf(key))
            } catch (e: Exception) {
                log.warn("redis pending delete failed for {}: {}", key, e.message)
                backoff(prefixOf(key))
            }
        }
        for (prefix in pendingPrefixes.toList()) {
            if (invalidateByPrefix(prefix)) {
                pendingPrefixes.remove(prefix)
                failNotBefore.remove(prefix)
            }
        }
    }

    private fun invalidateByPrefix(prefix: String): Boolean {
        return try {
            val options = ScanOptions.scanOptions().match("$prefix*").count(SCAN_BATCH.toLong()).build()
            redis.execute { connection ->
                connection.scan(options).use { cursor ->
                    var batch = mutableListOf<ByteArray>()
                    while (cursor.hasNext()) {
                        batch.add(cursor.next())
                        if (batch.size >= SCAN_BATCH) {
                            connection.keyCommands().del(*batch.toTypedArray())
                            batch = mutableListOf()
                        }
                    }
                    if (batch.isNotEmpty()) connection.keyCommands().del(*batch.toTypedArray())
                }
                null
            }
            true
        } catch (e: Exception) {
            log.warn("redis prefix cleanup failed for {}: {}", prefix, e.message)
            backoff(prefix)
            false
        }
    }

    companion object {
        const val BACKOFF_MILLIS = 5000L
        const val SCAN_BATCH = 256
    }
}
