package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 多实例失效同步：共享计数前进时各实例清空本地缓存；Redis 不可用退回单实例语义。 */
class SharedInvalidationTest {
    @Test
    fun `计数未前进与读取失败都不触发清理`() = runBlocking {
        val missing = SharedInvalidation(reader = { null })
        assertFalse(missing.advanced())
        val failing = SharedInvalidation(reader = { throw RuntimeException("redis down") })
        assertFalse(failing.advanced(), "Redis 不可用时退回本地语义，不能误清缓存")
    }

    @Test
    fun `计数前进返回 true 且只报一次`() = runBlocking {
        val epoch = AtomicLong(3)
        val sync = SharedInvalidation(reader = { epoch.get().toString() })
        assertTrue(sync.advanced())
        assertFalse(sync.advanced(), "同一计数不重复上报")
        epoch.incrementAndGet()
        assertTrue(sync.advanced())
    }

    @Test
    fun `广播失败不影响主流程`() = runBlocking {
        val sync = SharedInvalidation(writer = { throw RuntimeException("redis down") })
        sync.broadcast()
    }

    @Test
    fun `其它实例广播后，本实例的鉴权缓存被清空重新读库`() = runBlocking {
        val epoch = AtomicLong(0)
        val sync = SharedInvalidation(reader = { epoch.get().toString() }, writer = { epoch.incrementAndGet(); Unit })
        val store = CountingStore()
        val cache = AuthUserCache { 1_000_000L }
        val service = AuthService(store, TokenService(testSettings()), dummyHash = "unused", userCache = cache, sharedInvalidation = sync)

        assertNotNull(service.authenticate(2, 0))
        assertNotNull(service.authenticate(2, 0))
        assertEquals(1, store.loads.get(), "第二次鉴权命中缓存")

        // 模拟另一个实例完成了撤销并广播
        epoch.incrementAndGet()
        assertNotNull(service.authenticate(2, 0))
        assertEquals(2, store.loads.get(), "共享计数前进后必须清空本地缓存重新读库")
    }

    private fun testSettings() = AuthSettings(
        secret = "0123456789012345678901234567890123456789",
        issuer = "hr-api",
        audience = "hr-api-client",
        ttlSeconds = 3600,
    )

    private class CountingStore : AuthStore {
        val loads = AtomicInteger(0)
        private val user = AuthUser(
            id = 2,
            username = "reader",
            passwordHash = "unused",
            roleCode = "READER",
            enabled = true,
            roleEnabled = true,
            tokenVersion = 0,
        )

        override suspend fun findByUsername(username: String) = user
        override suspend fun findById(id: Int): AuthUser? {
            loads.incrementAndGet()
            return user
        }
        override suspend fun listUsers(limit: Int, offset: Long) = ApiList(1L, listOf(user.toDto()))
        override suspend fun listRoles() = listOf(RoleDto("READER", "Read only", true))
        override suspend fun createRole(code: String, name: String) = RoleDto(code, name, true)
        override suspend fun updateRole(code: String, body: RoleUpdateRequest) = null
        override suspend fun getRolePermissions(code: String) = null
        override suspend fun createUser(username: String, passwordHash: String, roleCode: String) = user
        override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?) = user
        override suspend fun deleteUser(id: Int) = false
        override suspend fun deleteRole(code: String) = false
        override suspend fun revokeTokens(id: Int) = Unit
        override suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto =
            throw UnsupportedOperationException()
    }
}
