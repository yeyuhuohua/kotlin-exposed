package com.atguigu.hr.auth

import com.atguigu.hr.common.api.respondOk
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 固定权限拦截的关键行为：403/401 响应发出后路由 handler 绝不能执行。
 * RoleAuthorization 只 respond 不抛异常，安全性依赖 Ktor 在响应提交后跳过 handler；
 * 重构 respondFail 或升级框架一旦改变该行为，这组测试会立即失败。
 */
class RoleAuthorizationHttpTest {
    private val handlerCalled = AtomicBoolean(false)

    private fun user(role: String, permissions: Set<String>) = AuthUser(
        id = 1,
        username = "tester",
        passwordHash = "unused",
        roleCode = role,
        enabled = true,
        roleEnabled = true,
        tokenVersion = 1,
        rolePermissions = permissions,
    )

    private fun Application.testModule() {
        install(ContentNegotiation) { json() }
        install(Authentication) {
            bearer("test") {
                authenticate { credential ->
                    when (credential.token) {
                        "reader" -> UserPrincipal(user(RoleCode.READER.name, PermissionCatalog.defaults(RoleCode.READER.name)))
                        "writer" -> UserPrincipal(user("WRITER", setOf("api:POST:/api/employees")))
                        else -> null
                    }
                }
            }
        }
        routing {
            route("/api") {
                authenticate("test") {
                    withBusinessPermissions {
                        post("/employees") {
                            handlerCalled.set(true)
                            call.respondOk("created")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `权限不足时返回 403 且 handler 不执行`() = testApplication {
        application { testModule() }
        val response = client.post("/api/employees") { bearerAuth("reader") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse(handlerCalled.get(), "403 响应后路由 handler 不得执行，否则写库副作用已经发生")
    }

    @Test
    fun `权限足够时 handler 正常执行`() = testApplication {
        application { testModule() }
        val response = client.post("/api/employees") { bearerAuth("writer") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(handlerCalled.get())
    }

    @Test
    fun `缺少凭证时返回 401 且 handler 不执行`() = testApplication {
        application { testModule() }
        val response = client.post("/api/employees")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertFalse(handlerCalled.get())
    }
}
