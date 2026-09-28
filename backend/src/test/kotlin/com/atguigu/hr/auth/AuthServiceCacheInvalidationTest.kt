package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `改密已提交但确认阶段抛错时缓存仍失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0))
        store.failAfterCommit = true
        val outcome = runCatching {
            service().updateUser(actorId = 1, id = 2, body = UserUpdateRequest(password = "new-password-1"))
        }
        assertTrue(outcome.isFailure, "存储层抛错必须向上传递")
        assertNull(cache.get(2, 0), "写库可能已提交，确认阶段抛错也必须失效缓存")
    }

    @Test
    fun `撤销令牌已提交但抛错时缓存仍失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0))
        store.failAfterCommit = true
        val outcome = runCatching { service().logout(2) }
        assertTrue(outcome.isFailure)
        assertNull(cache.get(2, 0))
    }

    @Test
    fun `权限调整已提交但抛错时角色缓存仍失效`() = runBlocking {
        assertNotNull(service().authenticate(2, 0))
        store.failAfterCommit = true
        val outcome = runCatching {
            service().updateRolePermissions(admin, "READER", RolePermissionsRequest(revision = 0, permissions = emptySet()))
        }
        assertTrue(outcome.isFailure)
        assertNull(cache.get(2, 0))
    }

    private class FakeStore : AuthStore {
        val users = mutableMapOf<Int, AuthUser>()

        /** 模拟"写入已提交但提交确认阶段连接异常"：先应用变更再抛错。 */
        var failAfterCommit = false

        private fun maybeFail() {
            if (failAfterCommit) throw RuntimeException("commit acknowledgement lost")
        }

        override suspend fun findByUsername(username: String) = users.values.firstOrNull { it.username == username }
        override suspend fun findById(id: Int) = users[id]
        override suspend fun listUsers(limit: Int, offset: Long) = ApiList(users.size.toLong(), users.values.map { it.toDto() })
        override suspend fun listRoles() = listOf(RoleDto("ADMIN", "Administrator", true), RoleDto("READER", "Read only", true))
        override suspend fun createRole(code: String, name: String) = RoleDto(code, name, true)
        override suspend fun updateRole(code: String, body: RoleUpdateRequest): RoleDto? {
            val dto = RoleDto(code, body.name ?: "Read only", body.enabled ?: true)
            maybeFail()
            return dto
        }
        override suspend fun getRolePermissions(code: String) =
            RolePermissionsDto(RoleDto(code, "Read only", true), 0, emptySet(), false)
        override suspend fun createUser(username: String, passwordHash: String, roleCode: String) = cachedUser
        override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?): AuthUser? {
            val updated = users[id]
            maybeFail()
            return updated
        }
        override suspend fun deleteUser(id: Int): Boolean {
            val deleted = users.remove(id) != null
            maybeFail()
            return deleted
        }
        override suspend fun deleteRole(code: String) = true
        override suspend fun revokeTokens(id: Int) = maybeFail()
        override suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto {
            val dto = RolePermissionsDto(RoleDto(code, "Read only", true), 1, request.permissions, false)
            maybeFail()
            return dto
        }
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
