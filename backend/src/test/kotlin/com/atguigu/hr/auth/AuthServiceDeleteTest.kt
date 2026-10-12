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
    private fun service(store: FakeStore, adminUsername: String = "admin") = AuthService(
        store, tokens = TokenService(testSettings()), dummyHash = "unused", adminUsername = adminUsername,
    )

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
    fun `配置的管理员和内置admin都不可删除`() = runBlocking {
        val store = FakeStore().apply {
            users[2] = user(2, "hr_owner", "ADMIN")
            users[3] = user(3, "admin", "ADMIN")
        }
        val service = service(store, " HR_OWNER ")
        for (id in listOf(2, 3)) {
            val error = assertFailsWith<AuthException> { service.deleteUser(actorId = 1, id = id) }
            assertEquals(ErrorCode.ADMIN_ACCOUNT_PROTECTED, error.error)
            assertEquals(HttpStatusCode.Forbidden, error.status)
            assertTrue(id in store.users)
        }
    }

    @Test
    fun `其他管理员不能停用降权或重置配置管理员密码`() = runBlocking {
        val store = FakeStore().apply { users[2] = user(2, "hr_owner", "ADMIN") }
        val original = store.users.getValue(2)
        val service = service(store, "hr_owner")
        for (request in listOf(
            UserUpdateRequest(enabled = false),
            UserUpdateRequest(roleCode = "READER"),
            UserUpdateRequest(password = "new-password-1"),
        )) {
            val error = assertFailsWith<AuthException> { service.updateUser(actorId = 1, id = 2, request) }
            assertEquals(ErrorCode.ADMIN_ACCOUNT_PROTECTED, error.error)
            assertEquals(original, store.users[2])
        }
    }

    @Test
    fun `配置管理员可以修改自己的密码`() = runBlocking {
        val store = FakeStore().apply { users[2] = user(2, "hr_owner", "ADMIN") }
        val result = service(store, "hr_owner").updateUser(2, 2, UserUpdateRequest(password = "new-password-1"))
        assertEquals("hr_owner", result.username)
        assertTrue(PasswordHasher.verify("new-password-1", store.users.getValue(2).passwordHash))
    }

    @Test
    fun `配置管理员的用户名不能由普通创建接口占用`() = runBlocking {
        val error = assertFailsWith<AuthException> {
            service(FakeStore(), "hr_owner").createUser(UserCreateRequest("HR_OWNER", "reader-pass-1"))
        }
        assertEquals(ErrorCode.USERNAME_TAKEN, error.error)
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

    @Test
    fun `保留用户名 admin 不能通过接口创建`() = runBlocking {
        // 初始管理员是自定义账号、库里还没有 admin 时，创建 admin/READER 会得到一个
        // 无法停用、删除或重置密码的账号；创建入口统一拒绝。
        val error = assertFailsWith<AuthException> {
            service(FakeStore()).createUser(UserCreateRequest(username = "admin", password = "reader-pass-1", roleCode = "READER"))
        }
        assertEquals(HttpStatusCode.Conflict, error.status)
        assertEquals(ErrorCode.USERNAME_TAKEN, error.error)
        assertTrue(service(FakeStore()).createUser(UserCreateRequest("admin2", "reader-pass-1", "READER")).username == "admin2")
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
        override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?) =
            users[id]?.let { original ->
                original.copy(
                    roleCode = roleCode ?: original.roleCode,
                    enabled = enabled ?: original.enabled,
                    passwordHash = passwordHash ?: original.passwordHash,
                ).also { users[id] = it }
            }
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
