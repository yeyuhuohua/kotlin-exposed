package com.atguigu.hr.common.cache

import com.atguigu.hr.config.RedisFactory
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 旁路缓存。
 *
 * - 失效按业务分组进行：每组各有一把锁和一份版本号，不同分组互不阻塞。
 * - 超时只计 Redis 命令本身，排队等待不会被误判成 Redis 不可用。
 * - 清理分组用 SCAN 游标，不用 KEYS，避免在请求路径上阻塞 Redis。
 * - 失效失败的分组保持旁路（dirty），清理成功后才重新启用，并在失败后退避一段时间。
 */
object RedisCache {
    private const val OPERATION_TIMEOUT_MS = 2_000L
    private const val RECOVER_BACKOFF_MS = 5_000L
    private const val SCAN_BATCH = 256L
    private const val DELETE_BATCH = 256
    private val log = LoggerFactory.getLogger(RedisCache::class.java)
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    internal data class Version(val number: Long = 0, val dirty: Boolean = false)

    internal enum class Group(vararg val patterns: String) {
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

        /** 同一分组内的清理与回填串行；不同分组各用各的锁。 */
        val lock = Mutex()

        /** 清理失败后的退避截止时间（System.nanoTime），退避期内直接旁路。 */
        val recoverNotBefore = AtomicLong(0L)
    }

    internal fun groupOf(key: String): Group? = when {
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

    private fun groupsOf(keys: Array<out String>): List<Group> =
        keys.map { requireNotNull(groupOf(it)) { "Unsupported cache key: $it" } }.distinct()

    /** 即使写入抛出异常，也可能已提交；多做一次失效比遗漏失效安全。 */
    suspend fun <T> withInvalidation(vararg keys: String, block: suspend () -> T): T {
        val groups = groupsOf(keys)
        currentCoroutineContext().ensureActive()
        val result = try {
            block()
        } finally {
            withContext(NonCancellable) { invalidate(groups) }
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
        fill(key, group, snap) { json.encodeToString(serializer, value) }
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
        if (value != null) fill(key, group, snap) { json.encodeToString(serializer, value) }
        return Cached(value, hit = false)
    }

    suspend fun evict(key: String) = evictAll(key)

    suspend fun evictByPrefix(prefix: String) = evictAll(prefix)

    suspend fun evictMatching(pattern: String) = evictAll(pattern.substringBefore('*'))

    suspend fun evictAll(vararg keys: String) = invalidate(groupsOf(keys))

    /** 先同步标记全部分组，再逐个清理；清理失败的分组保持旁路，不会重新暴露旧缓存。 */
    private suspend fun invalidate(groups: List<Group>) {
        groups.forEach { group -> group.version.updateAndGet { Version(it.number + 1, dirty = true) } }
        groups.forEach { group -> ensureClean(group) }
    }

    suspend fun readRaw(key: String): String? {
        val group = groupOf(key) ?: return null
        // 清理与退避在 cacheAttempt 之外，排队等待不算进 Redis 命令的超时预算。
        if (!ensureClean(group)) return null
        val before = group.version.get()
        if (before.dirty) return null
        return cacheAttempt("read $key") {
            val raw = RedisFactory.async.get(key).await()
            // 新的失效可能在等待 Redis GET 时标记分组。
            if (group.version.get() == before) raw else null
        }
    }

    /** 回填前重新核对版本，避免把失效期间读到的旧值写回缓存。 */
    private suspend fun fill(key: String, group: Group, snap: Long, encode: () -> String) {
        if (group.version.get().dirty) return
        group.lock.withLock {
            cacheAttempt("fill $key") {
                val version = group.version.get()
                if (!version.dirty && version.number == snap) {
                    RedisFactory.async.set(key, encode(), ttl()).await()
                }
            }
        }
    }

    /** 分组脏时清理一次；正在退避或清理失败时返回 false，调用方直接旁路。 */
    private suspend fun ensureClean(group: Group): Boolean {
        if (!group.version.get().dirty) return true
        if (System.nanoTime() < group.recoverNotBefore.get()) return false
        return group.lock.withLock {
            if (!group.version.get().dirty) return@withLock true
            // 排队期间可能已有请求清理失败并设置了退避，拿到锁后必须复查，否则串行重试拖慢请求
            if (System.nanoTime() < group.recoverNotBefore.get()) return@withLock false
            val cleaned = cacheAttempt("recover ${group.name}") { recover(group) }
            if (cleaned == null) group.recoverNotBefore.set(System.nanoTime() + RECOVER_BACKOFF_MS * 1_000_000)
            cleaned == true
        }
    }

    /** 必须持有该分组的锁。清理时不能只删某个 key，否则会留下同组的其它旧缓存。 */
    private suspend fun recover(group: Group): Boolean {
        val before = group.version.get()
        if (!before.dirty) return true
        val keys = group.patterns.flatMap { pattern -> scanKeys(pattern) }
        keys.chunked(DELETE_BATCH).forEach { batch -> RedisFactory.async.del(*batch.toTypedArray()).await() }
        // 清理期间若有新的失效，版本号会变，这里保持 dirty 让下次重试。
        return group.version.compareAndSet(before, Version(before.number))
    }

    /** 用 SCAN 游标遍历，KEYS 会阻塞 Redis 单线程，不能在请求路径上调用。 */
    private suspend fun scanKeys(pattern: String): List<String> {
        val keys = mutableListOf<String>()
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result: KeyScanCursor<String> = RedisFactory.async
                .scan(cursor, ScanArgs.Builder.matches(pattern).limit(SCAN_BATCH))
                .await()
            keys += result.keys
            cursor = result
        } while (!result.isFinished)
        return keys
    }

    private fun ttl(): SetArgs = SetArgs.Builder.ex(RedisFactory.ttlSeconds)

    private data class Outcome<T>(val value: T)

    /** 超时与异常都只记录日志并返回 null，缓存不可用时业务照常走数据库。 */
    private suspend fun <T> cacheAttempt(operation: String, block: suspend () -> T): T? = try {
        val outcome = withTimeoutOrNull(OPERATION_TIMEOUT_MS) { Outcome(block()) }
        if (outcome == null) log.warn("Redis {} timed out; cache bypassed", operation)
        outcome?.value
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("Redis {} failed; cache bypassed", operation, cause)
        null
    }
}
