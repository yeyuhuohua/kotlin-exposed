package com.atguigu.hr.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 缓存 key 必须带 tokenVersion，否则停用账号或改角色后会继续放行旧权限。 */
class AuthUserCacheTest {
    private var now = 1_000_000L

    private fun user(version: Int, roleCode: String = "READER") = AuthUser(
        id = 7,
        username = "reader",
        passwordHash = "unused",
        roleCode = roleCode,
        enabled = true,
        roleEnabled = true,
        tokenVersion = version,
        rolePermissions = setOf("page:employees"),
    )

    @Test
    fun `命中缓存并尊重 TTL`() {
        val cache = AuthUserCache(ttlMillis = 3_000) { now }
        cache.put(user(version = 1))
        assertNotNull(cache.get(7, 1))
        now += 2_999
        assertNotNull(cache.get(7, 1))
        now += 2
        assertNull(cache.get(7, 1), "TTL 到期后应重新查库")
    }

    @Test
    fun `tokenVersion 变化后不再命中旧权限`() {
        val cache = AuthUserCache(ttlMillis = 3_000) { now }
        cache.put(user(version = 1))
        assertNull(cache.get(7, 2), "版本升级（改密码/改角色/停用）必须立即失效")
    }

    @Test
    fun `关闭缓存时不读写`() {
        val cache = AuthUserCache(ttlMillis = 0) { now }
        cache.put(user(version = 1))
        assertNull(cache.get(7, 1))
        assertEquals(0, cache.trackedEntries())
    }

    @Test
    fun `条目数有上限`() {
        val cache = AuthUserCache(ttlMillis = 3_000, maxEntries = 5) { now }
        repeat(20) { index ->
            cache.put(user(version = index).copy(id = index))
        }
        assertEquals(true, cache.trackedEntries() <= 5)
    }

    @Test
    fun `invalidateUser 让该用户所有版本的条目立即失效`() {
        val cache = AuthUserCache(ttlMillis = 3_000) { now }
        cache.put(user(version = 1))
        cache.put(user(version = 2))
        cache.invalidateUser(7)
        assertNull(cache.get(7, 1), "退出或删号后旧版本条目不能再命中")
        assertNull(cache.get(7, 2))
        assertEquals(0, cache.trackedEntries())
    }

    @Test
    fun `invalidateRole 让该角色下所有用户的条目立即失效`() {
        val cache = AuthUserCache(ttlMillis = 3_000) { now }
        cache.put(user(version = 1, roleCode = "HR_VIEWER"))
        cache.put(user(version = 1).copy(id = 8, roleCode = "READER"))
        cache.invalidateRole("HR_VIEWER")
        assertNull(cache.get(7, 1), "角色权限调整后其成员的缓存不能再命中")
        assertNotNull(cache.get(8, 1), "其他角色的缓存不受影响")
    }

    @Test
    fun `失效前读出但失效后才回填的条目视为脏数据`() {
        val cache = AuthUserCache(ttlMillis = 3_000) { now }
        val loadedAt = cache.now()
        now += 1
        cache.invalidateUser(7)
        cache.put(user(version = 1), loadedAt)
        assertNull(cache.get(7, 1), "失效前从库里读到的旧数据不能回填")
        now += 1
        cache.put(user(version = 1), cache.now())
        assertNotNull(cache.get(7, 1), "失效之后重新读到的数据可以正常缓存")
    }
}
