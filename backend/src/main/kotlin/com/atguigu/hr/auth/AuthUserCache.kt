package com.atguigu.hr.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * 鉴权用的短 TTL 用户缓存（含角色权限），避免每个受保护请求都查一次库。
 *
 * key 里带 `tokenVersion`：改密码、改角色、停用账号都会 +1，因此这些操作立即失效。
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
    private class Entry(val user: AuthUser, val loadedAt: Long, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val invalidatedUsers = ConcurrentHashMap<Int, Long>()
    private val invalidatedRoles = ConcurrentHashMap<String, Long>()

    val enabled: Boolean get() = ttlMillis > 0

    /** 数据读出时刻，供 [put] 与失效时间比较。 */
    fun now(): Long = clock()

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
    fun put(user: AuthUser, loadedAt: Long = clock()) {
        if (!enabled) return
        val now = clock()
        // 拒绝两类回填：读出时刻早于失效时刻的脏数据，以及比 TTL 还老的迟到数据。
        // 后者保证失效标记被 prune 清理后，迟到的旧回填也不会让已撤销的 Token 复活。
        val entry = Entry(user, loadedAt, now + ttlMillis)
        if (loadedAt <= now - ttlMillis || entry.stale()) return
        if (entries.size >= maxEntries) prune()
        entries[key(user.id, user.tokenVersion)] = entry
    }

    /** 退出登录、改密码、停用或删除账号后调用：该用户所有 tokenVersion 的条目立即失效。 */
    fun invalidateUser(id: Int) {
        if (!enabled) return
        invalidatedUsers[id] = clock()
        entries.keys.removeIf { it.startsWith("$id:") }
    }

    /** 角色权限或启停变化后调用：该角色下所有用户的条目立即失效。 */
    fun invalidateRole(roleCode: String) {
        if (!enabled) return
        invalidatedRoles[roleCode] = clock()
        entries.entries.removeIf { it.value.user.roleCode == roleCode }
    }

    private fun Entry.stale(): Boolean =
        invalidatedUsers[user.id]?.let { loadedAt <= it } == true ||
            invalidatedRoles[user.roleCode]?.let { loadedAt <= it } == true

    fun trackedEntries(): Int = entries.size

    fun clear() {
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
