package com.atguigu.hr.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 鉴权用的短 TTL 用户缓存（含角色权限），避免每个受保护请求都查一次库。
 *
 * key 里带 `tokenVersion`：改密码、改角色、停用账号都会 +1，因此这些操作在本实例立即失效；
 * 多实例部署由 SharedInvalidation 同步，其它实例最迟下一个请求感知。
 * 退出登录、删除账号、调整角色权限等操作由 [invalidateUser] / [invalidateRole] 主动失效；
 * 条目记录数据读出的时刻，读出早于失效时刻的条目视为脏数据，挡住并发回填。
 * 直接在数据库里改数据时最多滞后 [ttlMillis]；设为 0 表示关闭缓存。
 * 只缓存通过校验的活跃用户，停用或已删除的账号不会被缓存成有效。
 */
class AuthUserCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = 5_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val user: AuthUser, val loadedAt: Long, val expiresAt: Long, val generation: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val invalidatedUsers = ConcurrentHashMap<Int, Long>()
    private val invalidatedRoles = ConcurrentHashMap<String, Long>()

    /** 跨实例同步触发整体清理时递增：clear 之前开始的查询属于旧代次，禁止回填。 */
    private val generation = AtomicLong(0)

    @Volatile
    private var lastPruneAt = 0L

    val enabled: Boolean get() = ttlMillis > 0

    /** 数据读出时刻与当时的代次，供 [put] 与失效时间、代次比较。 */
    fun now(): Long = clock()

    fun currentGeneration(): Long = generation.get()

    fun get(id: Int, tokenVersion: Int): AuthUser? {
        if (!enabled) return null
        val key = key(id, tokenVersion)
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= clock() || entry.stale()) {
            entries.remove(key, entry)
            return null
        }
        return entry.user
    }

    /** [loadedAt] 是数据从数据库读出的时刻；失效之后才回填的旧读数在这里被识别为脏数据。 */
    fun put(user: AuthUser, loadedAt: Long = clock(), loadedGeneration: Long = generation.get()) {
        if (!enabled) return
        val now = clock()
        // 拒绝三类回填：读出时刻早于失效时刻的脏数据、比 TTL 还老的迟到数据、
        // 整体清理（代次递增）之前开始的旧代次查询。
        val entry = Entry(user, loadedAt, now + ttlMillis, loadedGeneration)
        if (loadedAt <= now - ttlMillis || entry.stale()) return
        // 定期清理失效标记：与条目数解耦，否则低流量时过期标记会一直占着内存
        if (now - lastPruneAt >= ttlMillis || entries.size >= maxEntries) {
            lastPruneAt = now
            prune()
        }
        entries[key(user.id, user.tokenVersion)] = entry
    }

    /** 退出登录、改密码、停用或删除账号后调用：该用户所有 tokenVersion 的条目立即失效。 */
    fun invalidateUser(id: Int) {
        if (!enabled) return
        // 原子取最大值：交错的失效操作不允许让标记倒退，否则两次操作之间
        // 读出的旧数据会被当成新数据回填
        invalidatedUsers.merge(id, clock(), ::maxOf)
        entries.keys.removeIf { it.startsWith("$id:") }
    }

    /** 角色权限或启停变化后调用：该角色下所有用户的条目立即失效。 */
    fun invalidateRole(roleCode: String) {
        if (!enabled) return
        invalidatedRoles.merge(roleCode, clock(), ::maxOf)
        entries.entries.removeIf { it.value.user.roleCode == roleCode }
    }

    private fun Entry.stale(): Boolean =
        generation != this@AuthUserCache.generation.get() ||
            invalidatedUsers[user.id]?.let { loadedAt <= it } == true ||
            invalidatedRoles[user.roleCode]?.let { loadedAt <= it } == true

    fun trackedEntries(): Int = entries.size

    /** 失效标记数量；internal 供测试断言标记会被定期清理。 */
    internal fun trackedInvalidations(): Int = invalidatedUsers.size + invalidatedRoles.size

    /** 清空并递增代次：clear 之前开始的查询即使之后回填也会被拒绝。 */
    fun clear() {
        generation.incrementAndGet()
        entries.clear()
        invalidatedUsers.clear()
        invalidatedRoles.clear()
    }

    private fun key(id: Int, tokenVersion: Int) = "$id:$tokenVersion"

    private fun prune() {
        val now = clock()
        entries.entries.removeIf { it.value.expiresAt <= now }
        // 失效记录只需保留一个 TTL：put 会拒绝 loadedAt 早于 now-ttl 的数据，
        // 因此更老的失效记录对之后写入的条目不再有影响
        invalidatedUsers.entries.removeIf { it.value <= now - ttlMillis }
        invalidatedRoles.entries.removeIf { it.value <= now - ttlMillis }
        if (entries.size >= maxEntries) entries.clear()
    }

    companion object {
        /** 默认 3 秒：足够吸收同一页面并发请求，又不至于让库里的直接改动长时间不可见。 */
        const val DEFAULT_TTL_MILLIS = 3_000L
    }
}
