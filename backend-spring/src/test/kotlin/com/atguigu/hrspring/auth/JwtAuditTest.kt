package com.atguigu.hrspring.auth

import com.atguigu.hrspring.audit.AuditFilterConfig
import com.atguigu.hrspring.audit.AuditService
import com.atguigu.hrspring.common.api.ClientIp
import com.atguigu.hrspring.config.HrProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.FilterChain
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtAuditTest {
    private val properties = HrProperties(auth = HrProperties.AuthProperties(jwtSecret = "a".repeat(32)))
    private val tokens = TokenService(properties)
    private val user = AuthUser(7, "custom-admin", "ADMIN", true, true, 0, emptySet())
    private val loader = object : AuthUserLoader {
        override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? =
            user.takeIf { userId == it.id && tokenVersion == it.tokenVersion }
        override fun loadActiveAdmin(): AuthUser = user
    }

    private fun audit(path: String, token: String?, contextPath: String = ""): List<Array<out Any?>> {
        val service = mock(AuditService::class.java)
        val filter = requireNotNull(AuditFilterConfig(service, ClientIp(properties)).auditFilter().filter)
        val jwt = JwtAuthFilter(tokens, loader, jacksonObjectMapper())
        val request = MockHttpServletRequest("GET", contextPath + path).apply {
            this.contextPath = contextPath
        }
        if (token != null) request.addHeader("Authorization", "Bearer $token")
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, FilterChain { req, res ->
            jwt.doFilter(req, res, FilterChain { _, result ->
                (result as jakarta.servlet.http.HttpServletResponse).status = 200
            })
        })
        assertNull(CurrentUserHolder.get())
        return mockingDetails(service).invocations.filter { it.method.name == "recordApiCall" }
            .map { it.arguments }
    }

    @Test
    fun `JWT清理ThreadLocal后审计仍保留用户身份`() {
        val records = audit("/api/jobs", tokens.issue(7, 0))
        assertEquals(1, records.size)
        assertEquals(7, records.single()[0])
        assertEquals("custom-admin", records.single()[1])
        assertEquals(200, records.single()[5])
    }

    @Test
    fun `无效JWT提前返回401也只记录一次`() {
        val records = audit("/api/jobs", "invalid")
        assertEquals(1, records.size)
        assertNull(records.single()[0])
        assertEquals(401, records.single()[5])
    }

    @Test
    fun `健康检查不写审计`() {
        assertEquals(0, audit("/api/health", null).size)
        assertEquals(0, audit("/api/health", null, "/hr").size)
    }

    @Test
    fun `部署前缀下JWT身份和401仍被审计`() {
        val records = audit("/api/jobs", tokens.issue(7, 0), "/hr")
        assertEquals(7, records.single()[0])
        assertEquals("/hr/api/jobs", records.single()[3])
        assertEquals(401, audit("/api/jobs", "invalid", "/hr").single()[5])
    }
}
