package com.atguigu.hrspring.auth

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 进程内鉴权缓存:key 为 "$userId:$tokenVersion",短 TTL(默认 3 秒)。
 * 失效用代次(generation)阻止并发脏回填:读库前记录代次,代次变了拒绝回填。
 */
@Component
class AuthUserCache(
    properties: com.atguigu.hrspring.config.HrProperties,
) {
    private val ttlMillis = properties.auth.permissionCacheSeconds * 1000
    private val entries = ConcurrentHashMap<String, Entry>()
    private val generation = AtomicLong(0)
    private val lock = Any()

    private inner class Entry(
        val user: AuthUser,
        val loadedAt: Long,
    ) {
        val fresh: Boolean get() = System.currentTimeMillis() - loadedAt < ttlMillis
    }

    val enabled: Boolean get() = ttlMillis > 0

    fun get(userId: Int, tokenVersion: Int): AuthUser? {
        if (!enabled) return null
        val entry = entries["$userId:$tokenVersion"] ?: return null
        if (!entry.fresh) {
            entries.remove("$userId:$tokenVersion", entry)
            return null
        }
        return entry.user
    }

    /** 返回当前代次,供 load 前后比对。 */
    fun generation(): Long = generation.get()

    fun put(userId: Int, tokenVersion: Int, user: AuthUser, loadGeneration: Long) {
        if (!enabled) return
        synchronized(lock) {
            if (loadGeneration != generation.get()) return
            if (entries.size >= MAX_SIZE) entries.clear()
            entries["$userId:$tokenVersion"] = Entry(user, System.currentTimeMillis())
        }
    }

    fun invalidateUser(userId: Int) {
        synchronized(lock) {
            entries.keys.removeIf { it.startsWith("$userId:") }
            generation.incrementAndGet()
        }
    }

    /** 角色级失效:直接全清,简单且行为安全。 */
    fun invalidateAll() {
        synchronized(lock) {
            entries.clear()
            generation.incrementAndGet()
        }
    }

    companion object {
        const val MAX_SIZE = 5000
    }
}
