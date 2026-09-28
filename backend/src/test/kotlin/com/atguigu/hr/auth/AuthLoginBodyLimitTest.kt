package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** 登录请求读取前先限制体积：超限直接 413，不进入解析与业务校验。 */
class AuthLoginBodyLimitTest {
    private val dummyHash = runBlocking { PasswordHasher.hash("dummy-password") }

    private fun testModule(): Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        install(StatusPages) { authErrors() }
        routing {
            authPublicRoutes(
                AuthService(EmptyStore, TokenService(testSettings()), dummyHash = dummyHash),
                LoginThrottle(enabled = false),
            )
        }
    }

    private fun testSettings() = AuthSettings(
        secret = "0123456789012345678901234567890123456789",
        issuer = "hr-api",
        audience = "hr-api-client",
        ttlSeconds = 3600,
    )

    @Test
    fun `超过 4 KiB 的登录请求返回 413`() = testApplication {
        application(testModule())
        val fat = "x".repeat(2 * 1024 * 1024)
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"reader","password":"reader-pass-1","padding":"$fat"}""")
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }

    @Test
    fun `正常体积的登录请求进入业务校验`() = testApplication {
        application(testModule())
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"reader","password":"wrong-password"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `无 Content-Length 的分块请求同样按实际读取字节受限`() = testApplication {
        application(testModule())
        val fat = "x".repeat(8 * 1024)
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            // ByteReadChannel 不带长度，按分块传输发送
            setBody(
                ByteReadChannel(
                    """{"username":"reader","password":"reader-pass-1","padding":"$fat"}""".toByteArray(),
                ),
            )
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
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
