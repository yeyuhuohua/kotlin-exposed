package com.atguigu.hr.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * 鉴权用的短 TTL 用户缓存（含角色权限），避免每个受保护请求都查一次库。
 *
 * key 里带 `tokenVersion`：改密码、改角色、停用账号都会 +1，因此这些操作立即失效。
 * 直接在数据库里改数据时最多滞后 [ttlMillis]；设为 0 表示关闭缓存。
 * 只缓存通过校验的活跃用户，停用或已删除的账号不会被缓存成有效。
 */
class AuthUserCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = 5_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val user: AuthUser, val expiresAt: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    val enabled: Boolean get() = ttlMillis > 0

    fun get(id: Int, tokenVersion: Int): AuthUser? {
        if (!enabled) return null
        val key = key(id, tokenVersion)
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= clock()) {
            entries.remove(key, entry)
            return null
        }
        return entry.user
    }

    fun put(user: AuthUser) {
        if (!enabled) return
        if (entries.size >= maxEntries) prune()
        entries[key(user.id, user.tokenVersion)] = Entry(user, clock() + ttlMillis)
    }

    fun trackedEntries(): Int = entries.size

    fun clear() = entries.clear()

    private fun key(id: Int, tokenVersion: Int) = "$id:$tokenVersion"

    private fun prune() {
        val now = clock()
        entries.entries.removeIf { it.value.expiresAt <= now }
        if (entries.size >= maxEntries) entries.clear()
    }

    companion object {
        /** 默认 3 秒：足够吸收同一页面并发请求，又不至于让库里的直接改动长时间不可见。 */
        const val DEFAULT_TTL_MILLIS = 3_000L
    }
}
