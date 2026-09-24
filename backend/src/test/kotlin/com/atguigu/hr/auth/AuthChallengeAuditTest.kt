package com.atguigu.hr.auth

import com.atguigu.hr.audit.ApiCallAudit
import com.atguigu.hr.audit.ApiCallRecordInput
import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.common.api.respondOk
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 使用真实 JWT 配置：认证 challenge 返回 401 后，权限插件不得再重复响应，
 * 审计插件对一次请求只记录一条最终结果。
 */
class AuthChallengeAuditTest {
    private val collected = mutableListOf<ApiCallRecordInput>()

    private fun testModule(): Application.() -> Unit = {
        val tokens = TokenService(
            AuthSettings(
                secret = "0123456789012345678901234567890123456789",
                issuer = "hr-api",
                audience = "hr-api-client",
                ttlSeconds = 3600,
            ),
        )
        install(ContentNegotiation) { json() }
        installTokenAuthentication(AuthService(EmptyStore, tokens, dummyHash = "unused"), tokens)
        routing {
            route("/api") {
                install(ApiCallAudit) { sink = { collected.add(it) } }
                authenticate("auth-jwt") {
                    withBusinessPermissions {
                        post("/employees") { call.respondOk("created") }
                    }
                }
            }
        }
    }

    @Test
    fun `无 Token 的 401 只产生一条审计记录`() = testApplication {
        application(testModule())
        val response = client.post("/api/employees")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, collected.size, "challenge 与权限插件不能对同一次请求各记一条")
        assertEquals(401, collected.single().statusCode)
    }

    @Test
    fun `无效 Token 的 401 只产生一条审计记录`() = testApplication {
        application(testModule())
        val response = client.post("/api/employees") {
            headers.append("Authorization", "Bearer not-a-real-jwt")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, collected.size)
    }

    private object EmptyStore : AuthStore {
        override suspend fun findByUsername(username: String) = null
        override suspend fun findById(id: Int) = null
        override suspend fun listUsers(limit: Int, offset: Long) = ApiList(0L, emptyList<UserDto>())
        override suspend fun listRoles() = emptyList<RoleDto>()
        override suspend fun createRole(code: String, name: String) = RoleDto(code, name, true)
        override suspend fun updateRole(code: String, body: RoleUpdateRequest) = null
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
