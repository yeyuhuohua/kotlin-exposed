package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.common.api.ErrorCode
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * 删除的保护规则：内置 admin 账号、ADMIN 角色、当前登录账号都不可删，
 * 仍有账号引用的角色返回 409。这里用内存假仓储验证，不依赖数据库。
 */
class AuthServiceDeleteTest {
    private fun service(store: FakeStore) = AuthService(store, tokens = TokenService(testSettings()), dummyHash = "unused")

    private fun testSettings() = AuthSettings(
        secret = "0123456789012345678901234567890123456789",
        issuer = "hr-api",
        audience = "hr-api-client",
        ttlSeconds = 3600,
    )

    @Test
    fun `删除普通账号`() = runBlocking {
        val store = FakeStore().apply { users[2] = user(2, "reader") }
        assertTrue(service(store).deleteUser(actorId = 1, id = 2))
        assertFalse(2 in store.users)
    }

    @Test
    fun `内置 admin 账号不可删除`() = runBlocking {
        val store = FakeStore().apply { users[1] = user(1, "admin", "ADMIN"); users[2] = user(2, "reader") }
        val error = assertFailsWith<AuthException> { service(store).deleteUser(actorId = 2, id = 1) }
        assertEquals(HttpStatusCode.Forbidden, error.status)
        assertEquals(ErrorCode.ADMIN_ACCOUNT_PROTECTED, error.error)
        assertTrue(1 in store.users, "受保护账号不应被删除")
    }

    @Test
    fun `不能删除当前登录账号`() = runBlocking {
        val store = FakeStore().apply { users[3] = user(3, "operator", "ADMIN") }
        val error = assertFailsWith<AuthException> { service(store).deleteUser(actorId = 3, id = 3) }
        assertEquals(HttpStatusCode.BadRequest, error.status)
        assertEquals(ErrorCode.SELF_DELETION, error.error)
        assertTrue(3 in store.users)
    }

    @Test
    fun `账号不存在返回 404`() = runBlocking {
        val error = assertFailsWith<AuthException> { service(FakeStore()).deleteUser(actorId = 1, id = 99) }
        assertEquals(HttpStatusCode.NotFound, error.status)
    }

    @Test
    fun `ADMIN 角色不可删除`() = runBlocking {
        val error = assertFailsWith<AuthException> { service(FakeStore()).deleteRole(RoleCode.ADMIN.name) }
        assertEquals(HttpStatusCode.Forbidden, error.status)
        assertEquals(ErrorCode.ROLE_PROTECTED, error.error)
    }

    @Test
    fun `删除普通角色`() = runBlocking {
        val store = FakeStore().apply { roles["HR_VIEWER"] = RoleDto("HR_VIEWER", "人事查询员", true) }
        assertTrue(service(store).deleteRole("HR_VIEWER"))
        assertFalse("HR_VIEWER" in store.roles)
    }

    @Test
    fun `角色不存在返回 false`() = runBlocking {
        assertFalse(service(FakeStore()).deleteRole("MISSING"))
    }

    private class FakeStore : AuthStore {
        val users = mutableMapOf<Int, AuthUser>()
        val roles = mutableMapOf(RoleCode.ADMIN.name to RoleDto("ADMIN", "Administrator", true),
            RoleCode.READER.name to RoleDto("READER", "Read only", true))

        override suspend fun findByUsername(username: String) = users.values.firstOrNull { it.username == username }
        override suspend fun findById(id: Int) = users[id]
        override suspend fun listUsers(limit: Int, offset: Long) = ApiList(users.size.toLong(), users.values.map { it.toDto() })
        override suspend fun listRoles() = roles.values.toList()
        override suspend fun createRole(code: String, name: String) = RoleDto(code, name, true).also { roles[code] = it }
        override suspend fun updateRole(code: String, body: RoleUpdateRequest): RoleDto? = roles[code]
        override suspend fun getRolePermissions(code: String): RolePermissionsDto? = null
        override suspend fun createUser(username: String, passwordHash: String, roleCode: String) = user(99, username, roleCode)
        override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?) = users[id]
        override suspend fun revokeTokens(id: Int) = Unit
        override suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest) =
            RolePermissionsDto(roles.getValue(code), 1, request.permissions, false)

        override suspend fun deleteUser(id: Int) = users.remove(id) != null

        override suspend fun deleteRole(code: String): Boolean {
            val members = users.values.count { it.roleCode == code }
            if (members > 0) {
                throw AuthException(HttpStatusCode.Conflict, "role still has $members user(s)", ErrorCode.ROLE_IN_USE)
            }
            return roles.remove(code) != null
        }
    }
}

private fun user(id: Int, username: String, role: String = "READER") = AuthUser(
    id = id,
    username = username,
    passwordHash = "pbkdf2-sha256\$600000\$x\$y",
    roleCode = role,
    enabled = true,
    roleEnabled = true,
    tokenVersion = 0,
)
