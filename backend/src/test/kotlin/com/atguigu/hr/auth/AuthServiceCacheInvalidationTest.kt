package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 权限变更必须同步失效鉴权缓存：旧 Token 在变更后不能继续命中缓存放行。 */
class AuthServiceCacheInvalidationTest {
    private val cache = AuthUserCache { 1_000_000L }
    private val store = FakeStore().apply { users[2] = cachedUser }

    private fun service() = AuthService(store, TokenService(testSettings()), dummyHash = "unused", userCache = cache)

    private fun testSettings() = AuthSettings(
        secret = "0123456789012345678901234567890123456789",
        issuer = "hr-api",
        audience = "hr-api-client",
        ttlSeconds = 3600,
    )

    @Test
    fun `退出登录后旧版本缓存立即失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0), "第一次鉴权应成功并写入缓存")
        service().logout(2)
        assertNull(cache.get(2, 0), "退出后旧 Token 不能再命中缓存")
    }

    @Test
    fun `删除账号后旧版本缓存立即失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0))
        service().deleteUser(actorId = 1, id = 2)
        assertNull(cache.get(2, 0))
    }

    @Test
    fun `修改账号后旧版本缓存立即失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0))
        service().updateUser(actorId = 1, id = 2, body = UserUpdateRequest(enabled = false))
        assertNull(cache.get(2, 0))
    }

    @Test
    fun `调整角色权限后该角色的缓存立即失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0))
        service().updateRolePermissions(admin, "READER", RolePermissionsRequest(revision = 0, permissions = emptySet()))
        assertNull(cache.get(2, 0))
    }

    private class FakeStore : AuthStore {
        val users = mutableMapOf<Int, AuthUser>()

        override suspend fun findByUsername(username: String) = users.values.firstOrNull { it.username == username }
        override suspend fun findById(id: Int) = users[id]
        override suspend fun listUsers(limit: Int, offset: Long) = ApiList(users.size.toLong(), users.values.map { it.toDto() })
        override suspend fun listRoles() = listOf(RoleDto("ADMIN", "Administrator", true), RoleDto("READER", "Read only", true))
        override suspend fun createRole(code: String, name: String) = RoleDto(code, name, true)
        override suspend fun updateRole(code: String, body: RoleUpdateRequest) = RoleDto(code, "Read only", true)
        override suspend fun getRolePermissions(code: String) =
            RolePermissionsDto(RoleDto(code, "Read only", true), 0, emptySet(), false)
        override suspend fun createUser(username: String, passwordHash: String, roleCode: String) = cachedUser
        override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?) = users[id]
        override suspend fun deleteUser(id: Int) = users.remove(id) != null
        override suspend fun deleteRole(code: String) = true
        override suspend fun revokeTokens(id: Int) = Unit
        override suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest) =
            RolePermissionsDto(RoleDto(code, "Read only", true), 1, request.permissions, false)
    }

    private companion object {
        val cachedUser = AuthUser(
            id = 2,
            username = "reader",
            passwordHash = "unused",
            roleCode = "READER",
            enabled = true,
            roleEnabled = true,
            tokenVersion = 0,
        )
        val admin = cachedUser.copy(
            id = 1,
            username = "admin",
            roleCode = RoleCode.ADMIN.name,
            rolePermissions = setOf("api:PUT:/api/auth/roles/{code}/permissions"),
        )
    }
}
