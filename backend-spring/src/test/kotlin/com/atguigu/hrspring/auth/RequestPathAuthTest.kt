package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.GlobalExceptionHandler
import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.controller.DepartmentController
import com.atguigu.hrspring.service.DepartmentService
import com.atguigu.hrspring.service.EmployeeService
import tools.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.MappingMatch
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletMapping
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertNull

class RequestPathAuthTest {
    private val tokens = TokenService(HrProperties(auth = HrProperties.AuthProperties(jwtSecret = "a".repeat(32))))
    private val reader = AuthUser(7, "reader", Roles.READER, true, true, 0, setOf("api:GET:/api/departments"))
    private val loader = object : AuthUserLoader {
        override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? =
            reader.takeIf { it.id == userId && it.tokenVersion == tokenVersion }
        override fun loadActiveAdmin(): AuthUser? = null
    }
    private val departments = mock(DepartmentService::class.java)
    private val employees = mock(EmployeeService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(
        DepartmentController(departments, employees), PublicPathController(),
    ).setControllerAdvice(GlobalExceptionHandler())
        .addInterceptors(PermissionInterceptor())
        .addFilters<StandaloneMockMvcBuilder>(JwtAuthFilter(tokens, loader, jacksonObjectMapper()))
        .build()

    @Test
    fun `部署前缀不允许未登录读取或写入部门`() {
        for (context in listOf("", "/hr")) {
            mvc.perform(get("$context/api/departments").contextPath(context))
                .andExpect(status().isUnauthorized)
            mvc.perform(post("$context/api/departments").contextPath(context)
                .contentType("application/json").content("""{"departmentName":"Review"}"""))
                .andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(departments, employees)
        assertNull(CurrentUserHolder.get())
    }

    @Test
    fun `部署前缀下有效JWT仍按目录校验权限`() {
        `when`(departments.list()).thenReturn(emptyList())
        val token = tokens.issue(reader.id, reader.tokenVersion)
        mvc.perform(get("/hr/api/departments").contextPath("/hr").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
        mvc.perform(post("/hr/api/departments").contextPath("/hr").header("Authorization", "Bearer $token")
            .contentType("application/json").content("""{"departmentName":"Review"}"""))
            .andExpect(status().isForbidden)
        assertNull(CurrentUserHolder.get())
    }

    @Test
    fun `部署前缀下无效JWT拒绝访问`() {
        mvc.perform(get("/hr/api/departments").contextPath("/hr").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(departments, employees)
    }

    @Test
    fun `Bearer认证方案按HTTP规则忽略大小写`() {
        `when`(departments.list()).thenReturn(emptyList())
        val token = tokens.issue(reader.id, reader.tokenVersion)
        for (scheme in listOf("Bearer", "bearer", "bEaReR")) {
            mvc.perform(get("/hr/api/departments").contextPath("/hr")
                .header("Authorization", "$scheme $token"))
                .andExpect(status().isOk)
        }
        mvc.perform(get("/hr/api/departments").contextPath("/hr")
            .header("Authorization", "Basic $token"))
            .andExpect(status().isUnauthorized)
        assertNull(CurrentUserHolder.get())
    }

    @Test
    fun `servlet映射前缀同样不能绕过登录`() {
        mvc.perform(get("/hr/gateway/api/departments").contextPath("/hr").servletPath("/gateway")
            .pathInfo("/api/departments").with { request ->
                request.setHttpServletMapping(
                    MockHttpServletMapping("api/departments", "/gateway/*", "dispatcherServlet", MappingMatch.PATH),
                )
                request
            })
            .andExpect(status().isUnauthorized)
        verifyNoInteractions(departments, employees)
    }

    @Test
    fun `编码路径和矩阵参数不能绕过登录`() {
        for (path in listOf("/hr/%61pi/departments", "/hr/api;v=1/departments")) {
            mvc.perform(get(java.net.URI.create(path)).contextPath("/hr"))
                .andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(departments, employees)
    }

    @Test
    fun `部署前缀下公开接口仍然可访问而个人信息必须登录`() {
        mvc.perform(get("/hr/api/health").contextPath("/hr")).andExpect(status().isOk)
        mvc.perform(post("/hr/api/auth/login").contextPath("/hr")).andExpect(status().isOk)
        mvc.perform(get("/hr/api/auth/me").contextPath("/hr")).andExpect(status().isUnauthorized)
        mvc.perform(get("/hr/api/auth/me").contextPath("/hr")
            .header("Authorization", "Bearer ${tokens.issue(reader.id, reader.tokenVersion)}"))
            .andExpect(status().isOk)
    }
}

@RestController
private class PublicPathController {
    @GetMapping("/api/health", "/api/auth/me")
    fun get() = "ok"

    @PostMapping("/api/auth/login")
    fun login() = "ok"
}
