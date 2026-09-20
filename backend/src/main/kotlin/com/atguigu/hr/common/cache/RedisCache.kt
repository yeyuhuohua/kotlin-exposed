package com.atguigu.hr.common.cache

import com.atguigu.hr.config.RedisFactory
import io.lettuce.core.SetArgs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/** 旁路缓存。失效失败的业务分组保持旁路，清理成功后才重新启用。 */
object RedisCache {
    private const val OPERATION_TIMEOUT_MS = 2_000L
    private val log = LoggerFactory.getLogger(RedisCache::class.java)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val gate = Mutex()

    private data class Version(val number: Long = 0, val dirty: Boolean = false)

    private enum class Group(vararg val patterns: String) {
        EMPLOYEES("hr:employees:*"),
        EMPLOYEE("hr:employee:*"),
        EMP_DETAILS("hr:emp-details:*"),
        DEPARTMENT("hr:department:*", "hr:departments"),
        JOBS("hr:jobs"),
        LOCATIONS("hr:locations"),
        GEOGRAPHY("hr:countries", "hr:regions"),
        JOB_HISTORY("hr:job-history"),
        JOB_GRADES("hr:job-grades"),
        T_DEPT("hr:t-dept"),
        T_EMP("hr:t-emp"),
        ORDERS("hr:orders"),
        OVERVIEW("hr:overview");

        // 重启会丢失上次失效失败的标记，首次使用前先清理遗留缓存。
        val version = AtomicReference(Version(dirty = true))
    }

    private fun groupOf(key: String): Group? = when {
        key.startsWith("hr:employees:") -> Group.EMPLOYEES
        key.startsWith("hr:employee:") -> Group.EMPLOYEE
        key.startsWith("hr:emp-details:") -> Group.EMP_DETAILS
        key == "hr:departments" || key.startsWith("hr:department:") -> Group.DEPARTMENT
        key == "hr:jobs" -> Group.JOBS
        key == "hr:locations" -> Group.LOCATIONS
        key == "hr:countries" || key == "hr:regions" -> Group.GEOGRAPHY
        key == "hr:job-history" -> Group.JOB_HISTORY
        key == "hr:job-grades" -> Group.JOB_GRADES
        key == "hr:t-dept" -> Group.T_DEPT
        key == "hr:t-emp" -> Group.T_EMP
        key == "hr:orders" -> Group.ORDERS
        key == "hr:overview" -> Group.OVERVIEW
        else -> null
    }

    /** 即使写入抛出异常，也可能已提交；多做一次失效比遗漏失效安全。 */
    suspend fun <T> withInvalidation(vararg keys: String, block: suspend () -> T): T {
        require(keys.all { groupOf(it) != null }) { "Unsupported cache invalidation group" }
        currentCoroutineContext().ensureActive()
        val result = try {
            block()
        } finally {
            withContext(NonCancellable) { evictAll(*keys) }
        }
        currentCoroutineContext().ensureActive()
        return result
    }

    suspend inline fun <reified T> getOrLoad(key: String, noinline loader: suspend () -> T): Cached<T> =
        getOrLoad(key, serializer(), loader)

    suspend fun <T> getOrLoad(
        key: String,
        serializer: KSerializer<T>,
        loader: suspend () -> T,
    ): Cached<T> {
        val group = groupOf(key) ?: return Cached(loader(), hit = false)
        val snap = group.version.get().number
        readRaw(key)?.let { raw ->
            runCatching { json.decodeFromString(serializer, raw) }
                .onSuccess { return Cached(it, hit = true) }
                .onFailure { log.warn("Cache decode failed for {}", key, it) }
        }
        val value = loader()
        cacheAttempt("fill $key") {
            val version = group.version.get()
            if (!version.dirty && version.number == snap) {
                RedisFactory.async.set(key, json.encodeToString(serializer, value), ttl()).await()
            }
        }
        return Cached(value, hit = false)
    }

    suspend inline fun <reified T : Any> getOrLoadNullable(
        key: String,
        noinline loader: suspend () -> T?,
    ): Cached<T?> = getOrLoadNullable(key, serializer(), loader)

    suspend fun <T : Any> getOrLoadNullable(
        key: String,
        serializer: KSerializer<T>,
        loader: suspend () -> T?,
    ): Cached<T?> {
        val group = groupOf(key) ?: return Cached(loader(), hit = false)
        val snap = group.version.get().number
        readRaw(key)?.let { raw ->
            runCatching { json.decodeFromString(serializer, raw) }
                .onSuccess { return Cached(it, hit = true) }
                .onFailure { log.warn("Cache decode failed for {}", key, it) }
        }
        val value = loader()
        if (value != null) {
            cacheAttempt("fill $key") {
                val version = group.version.get()
                if (!version.dirty && version.number == snap) {
                    RedisFactory.async.set(key, json.encodeToString(serializer, value), ttl()).await()
                }
            }
        }
        return Cached(value, hit = false)
    }

    suspend fun evict(key: String) = evictAll(key)

    suspend fun evictByPrefix(prefix: String) = evictAll(prefix)

    suspend fun evictMatching(pattern: String) = evictAll(pattern.substringBefore('*'))

    /** 先同步标记全部分组，再等待锁；超时、取消或删除失败都不会重新暴露旧缓存。 */
    suspend fun evictAll(vararg keys: String) {
        val groups = keys.map { requireNotNull(groupOf(it)) { "Unsupported cache key: $it" } }.distinct()
        groups.forEach { group -> group.version.updateAndGet { Version(it.number + 1, dirty = true) } }
        cacheAttempt("invalidate ${groups.joinToString()}") {
            groups.forEach { recover(it) }
        }
    }

    suspend fun readRaw(key: String): String? {
        val group = groupOf(key) ?: return null
        return cacheAttempt("read $key") {
            if (!recover(group)) return@cacheAttempt null
            val before = group.version.get()
            if (before.dirty) return@cacheAttempt null
            val raw = RedisFactory.async.get(key).await()
            // 新的失效可能在等待 Redis GET 时标记分组。
            if (group.version.get() == before) raw else null
        }
    }

    /** 必须持有 gate。恢复时清理整个分组，不能用某个 key 的删除替代之前失败的清理。 */
    private suspend fun recover(group: Group): Boolean {
        val before = group.version.get()
        if (!before.dirty) return true
        for (pattern in group.patterns) {
            val keys = if ('*' in pattern) RedisFactory.async.keys(pattern).await() else listOf(pattern)
            if (keys.isNotEmpty()) RedisFactory.async.del(*keys.toTypedArray()).await()
        }
        return group.version.compareAndSet(before, Version(before.number))
    }

    private fun ttl(): SetArgs = SetArgs.Builder.ex(RedisFactory.ttlSeconds)

    private data class Outcome<T>(val value: T)

    private suspend fun <T> cacheAttempt(operation: String, block: suspend () -> T): T? = try {
        val outcome = withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
            gate.withLock { Outcome(block()) }
        }
        if (outcome == null) log.warn("Redis {} timed out; cache bypassed", operation)
        outcome?.value
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("Redis {} failed; cache bypassed", operation, cause)
        null
    }
}
