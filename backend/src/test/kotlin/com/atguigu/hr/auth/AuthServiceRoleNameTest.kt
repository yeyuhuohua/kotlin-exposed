package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 角色名称与 name 列 varchar(50) 的码点计长一致：增补平面字符按 1 个计。 */
class AuthServiceRoleNameTest {
    private fun service() = AuthService(FakeStore(), TokenService(testSettings()), dummyHash = "unused")

    private fun testSettings() = AuthSettings(
        secret = "0123456789012345678901234567890123456789",
        issuer = "hr-api",
        audience = "hr-api-client",
        ttlSeconds = 3600,
    )

    @Test
    fun `50 个码点的角色名称放行`() = runBlocking {
        // 𠮷 占 1 个码点、2 个 UTF-16 单元：50 个共 100 个 UTF-16 单元
        val role = service().createRole(RoleCreateRequest(code = "HR_VIEWER", name = "𠮷".repeat(50)))
        assertEquals("HR_VIEWER", role.code)
    }

    @Test
    fun `51 个码点的角色名称被拒绝`() = runBlocking {
        val error = assertFailsWith<AuthException> {
            service().createRole(RoleCreateRequest(code = "HR_VIEWER", name = "𠮷".repeat(51)))
        }
        assertEquals(HttpStatusCode.BadRequest, error.status)
    }

    @Test
    fun `更新角色名称同样按码点计数`() = runBlocking {
        val error = assertFailsWith<AuthException> {
            service().updateRole("HR_VIEWER", RoleUpdateRequest(name = "𠮷".repeat(51)))
        }
        assertEquals(HttpStatusCode.BadRequest, error.status)
    }

    private class FakeStore : AuthStore {
        override suspend fun findByUsername(username: String) = null
        override suspend fun findById(id: Int) = null
        override suspend fun listUsers(limit: Int, offset: Long) = ApiList(0L, emptyList<UserDto>())
        override suspend fun listRoles() = listOf(RoleDto("READER", "Read only", true))
        override suspend fun createRole(code: String, name: String) = RoleDto(code, name, true)
        override suspend fun updateRole(code: String, body: RoleUpdateRequest) = RoleDto(code, "Read only", true)
        override suspend fun getRolePermissions(code: String) = null
        override suspend fun createUser(username: String, passwordHash: String, roleCode: String): AuthUser =
            throw UnsupportedOperationException()
        override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?) = null
        override suspend fun deleteUser(id: Int) = false
        override suspend fun deleteRole(code: String) = false
        override suspend fun revokeTokens(id: Int) = Unit
        override suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto =
            throw UnsupportedOperationException()
    }
}
