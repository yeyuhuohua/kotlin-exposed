package com.atguigu.hr.common.cache

import com.atguigu.hr.config.RedisFactory
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * - 读取或回填失败同样进入退避：Redis 故障期间请求直接回源，不会持锁逐个等待超时。
 * - 多实例：每组另有共享版本号，随恢复流程原子递增；缓存值内嵌所属版本，
 *   读取校验版本、回填用 Lua 原子"版本未变才写入"，其它实例的旧值与旧回填都不会复活。
 */
object RedisCache {
    private const val OPERATION_TIMEOUT_MS = 2_000L
    private const val RECOVER_BACKOFF_MS = 5_000L
    private const val SCAN_BATCH = 256L
    private const val DELETE_BATCH = 256

    /** 原子回填：共享版本令牌未变才写入；返回 1 写入、0 丢弃（其它实例已失效该分组）。 */
    private const val CHECK_AND_SET = """
local current = redis.call('GET', KEYS[2]) or ''
if current ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
return 1
"""
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

        /** 读取或回填失败后的退避截止时间；退避期内读缓存与回填都直接跳过。 */
        val failNotBefore = AtomicLong(0L)
    }

    /** 聚合分组的依赖：依赖分组恢复后，聚合缓存必须再次失效（清理窗口内可能已用旧数据生成新聚合）。 */
    private val dependents: Map<Group, List<Group>> = mapOf(
        Group.EMPLOYEES to listOf(Group.OVERVIEW),
        Group.EMPLOYEE to listOf(Group.OVERVIEW),
        Group.EMP_DETAILS to listOf(Group.OVERVIEW),
        Group.DEPARTMENT to listOf(Group.OVERVIEW),
        Group.JOBS to listOf(Group.OVERVIEW),
        Group.LOCATIONS to listOf(Group.OVERVIEW),
        Group.GEOGRAPHY to listOf(Group.OVERVIEW),
        Group.JOB_HISTORY to listOf(Group.OVERVIEW),
        Group.JOB_GRADES to listOf(Group.OVERVIEW),
        Group.T_DEPT to listOf(Group.OVERVIEW),
        Group.T_EMP to listOf(Group.OVERVIEW),
    )

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
        val raw = readRaw(key)
        // 共享版本在恢复之后、回源之前读取：快照必须早于 loader，又不能早于 recover 的版本递增
        val sharedSnap = sharedVersion(group)
        raw?.let {
            // 缓存值内嵌所属版本：版本对不上（其它实例失效后的清理窗口、
            // 以及共享版本不可确认）一律视为未命中回源
            versionedData(it, sharedSnap)?.let { data ->
                runCatching { json.decodeFromJsonElement(serializer, data) }
                    .onSuccess { return Cached(it, hit = true) }
                    .onFailure { log.warn("Cache decode failed for {}", key, it) }
            }
        }
        val value = loader()
        fill(key, group, snap, sharedSnap) { json.encodeToString(serializer, value) }
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
        val raw = readRaw(key)
        val sharedSnap = sharedVersion(group)
        raw?.let {
            versionedData(it, sharedSnap)?.let { data ->
                runCatching { json.decodeFromJsonElement(serializer, data) }
                    .onSuccess { return Cached(it, hit = true) }
                    .onFailure { log.warn("Cache decode failed for {}", key, it) }
            }
        }
        val value = loader()
        if (value != null) fill(key, group, snap, sharedSnap) { json.encodeToString(serializer, value) }
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

    /** 共享版本 key 不在任何清理模式的命名空间内，recover 的 SCAN 不会碰到它。 */
    private fun versionKey(group: Group) = "hr:cache:ver:${group.name}"

    /** 版本令牌随机生成：键丢失重建也不会复用旧值（区别于自增计数）。 */
    private fun newVersionToken() = java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    /**
     * 读共享版本令牌：key 不存在视为空令牌（第一次失效前的初始版本）。
     * 与读取/回填遵守同一套退避规则；查询失败进入退避并返回 null，
     * 调用方在版本不可确认时只回源、不回填。
     */
    private suspend fun sharedVersion(group: Group): String? {
        if (System.nanoTime() < group.failNotBefore.get()) return null
        if (group.version.get().dirty && System.nanoTime() < group.recoverNotBefore.get()) return null
        val outcome = cacheOutcome("version ${group.name}") { RedisFactory.async.get(versionKey(group)).await() }
        if (outcome == null) {
            group.backoffOnFailure()
            return null
        }
        return outcome.value ?: ""
    }

    suspend fun readRaw(key: String): String? {
        val group = groupOf(key) ?: return null
        // 清理与退避在 cacheOutcome 之外，排队等待不算进 Redis 命令的超时预算。
        if (!ensureClean(group)) return null
        val before = group.version.get()
        if (before.dirty) return null
        if (System.nanoTime() < group.failNotBefore.get()) return null
        val outcome = cacheOutcome("read $key") {
            val raw = RedisFactory.async.get(key).await()
            // 新的失效可能在等待 Redis GET 时标记分组。
            if (group.version.get() == before) raw else null
        }
        // 读取失败（超时/异常）进入退避；缓存未命中不算失败。
        if (outcome == null) group.backoffOnFailure()
        return outcome?.value
    }

    /** 回填前重新核对版本，避免把失效期间读到的旧值写回缓存。 */
    private suspend fun fill(key: String, group: Group, snap: Long, sharedSnap: String?, encode: () -> String) {
        if (group.version.get().dirty) return
        if (System.nanoTime() < group.failNotBefore.get()) return
        // 版本不可确认时只回源、不回填：无条件 SET 会绕过跨实例保护
        if (sharedSnap == null) return
        group.lock.withLock {
            // 排队期间可能已有回填失败并设置了退避，拿到锁后必须复查，否则串行等待超时
            if (System.nanoTime() < group.failNotBefore.get()) return@withLock
            val outcome = cacheOutcome("fill $key") {
                val version = group.version.get()
                if (!version.dirty && version.number == snap) {
                    // 值内嵌所属版本令牌；原子校验令牌再写入，其它实例失效后旧回填被拒
                    RedisFactory.async.eval<Long>(
                        CHECK_AND_SET,
                        ScriptOutputType.INTEGER,
                        arrayOf(key, versionKey(group)),
                        sharedSnap,
                        """{"v":"$sharedSnap","d":${encode()}}""",
                        RedisFactory.ttlSeconds.toString(),
                    ).await()
                }
            }
            if (outcome == null) group.backoffOnFailure()
        }
    }

    /** 解开内嵌版本令牌：令牌不符、格式不符（旧格式值）或共享版本不可确认时返回 null。 */
    private fun versionedData(raw: String, expectedVersion: String?): kotlinx.serialization.json.JsonElement? {
        if (expectedVersion == null) return null
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val version = obj["v"]?.jsonPrimitive?.contentOrNull ?: return null
            if (version != expectedVersion) return null
            obj["d"] ?: return null
        }.getOrNull()
    }

    private fun Group.backoffOnFailure() {
        failNotBefore.set(System.nanoTime() + RECOVER_BACKOFF_MS * 1_000_000)
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
            // 恢复失败（含共享版本递增失败）一律退避重试，不放宽跨实例保护
            if (cleaned != true) group.recoverNotBefore.set(System.nanoTime() + RECOVER_BACKOFF_MS * 1_000_000)
            cleaned == true
        }
    }

    /**
     * 必须持有该分组的锁。恢复 = 写入新版本令牌 + 清理整组缓存：
     * 连接故障返回 false 由上层退避重试；并发失效导致 CAS 失败不是故障，
     * 继续处理最新版本，不漏掉它的共享失效。
     * 清理时不能只删某个 key，否则会留下同组的其它旧缓存。
     */
    private suspend fun recover(group: Group): Boolean {
        while (true) {
            val before = group.version.get()
            if (!before.dirty) return true
            val bumped = cacheOutcome("bump ${group.name}") {
                RedisFactory.async.set(versionKey(group), newVersionToken()).await()
            } ?: return false
            val keys = group.patterns.flatMap { pattern -> scanKeys(pattern) }
            keys.chunked(DELETE_BATCH).forEach { batch -> RedisFactory.async.del(*batch.toTypedArray()).await() }
            if (group.version.compareAndSet(before, Version(before.number))) {
                // 依赖它的聚合缓存再次失效：恢复前的窗口里，其它实例可能已用旧数据生成新聚合。
                // 聚合分组已在失效流程（dirty）时由它自己的恢复覆盖，无需重复级联。
                dependents[group]
                    ?.filter { dependent -> !dependent.version.get().dirty }
                    ?.forEach { dependent -> invalidate(listOf(dependent)) }
                return true
            }
        }
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

    /** 返回 null 表示超时或异常；成功时即使值为 null（如缓存未命中）也包装在 Outcome 里返回。 */
    private suspend fun <T> cacheOutcome(operation: String, block: suspend () -> T): Outcome<T>? = try {
        val outcome = withTimeoutOrNull(OPERATION_TIMEOUT_MS) { Outcome(block()) }
        if (outcome == null) log.warn("Redis {} timed out; cache bypassed", operation)
        outcome
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        log.warn("Redis {} failed; cache bypassed", operation, cause)
        null
    }

    /** 超时与异常都只记录日志并返回 null，缓存不可用时业务照常走数据库。 */
    private suspend fun <T> cacheAttempt(operation: String, block: suspend () -> T): T? =
        cacheOutcome(operation, block)?.value
}
