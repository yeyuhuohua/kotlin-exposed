package com.atguigu.hrspring.config

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.AuthUserLoader
import com.atguigu.hrspring.auth.CurrentUserDto
import com.atguigu.hrspring.auth.JwtAuthFilter
import com.atguigu.hrspring.auth.PermissionInterceptor
import com.atguigu.hrspring.auth.TokenDto
import com.atguigu.hrspring.auth.TokenService
import com.atguigu.hrspring.controller.AuthController
import com.atguigu.hrspring.dto.LoginRequest
import com.atguigu.hrspring.service.AuthService
import jakarta.servlet.Filter
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertNull

class CorsAuthTest {
    private fun withMvc(check: (MockMvc) -> Unit) {
        val tokens = TokenService(HrProperties(auth = HrProperties.AuthProperties(jwtSecret = "a".repeat(32))))
        val reader = AuthUser(7, "reader", "READER", true, true, 0, emptySet())
        val user = CurrentUserDto(reader.id, reader.username, reader.roleCode, true, emptySet())
        val service = mock(AuthService::class.java)
        `when`(service.login(LoginRequest("reader", "test-password")))
            .thenReturn(TokenDto(tokens.issue(reader.id, 0), tokens.ttlSeconds, user))
        `when`(service.currentUser(reader)).thenReturn(user)
        `when`(service.pageRoutes(reader)).thenReturn(emptyList())
        val loader = object : AuthUserLoader {
            override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? =
                reader.takeIf { it.id == userId && it.tokenVersion == tokenVersion }

            override fun loadActiveAdmin(): AuthUser? = null
        }
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java, WebMvcAutoConfiguration::class.java))
            .withUserConfiguration(WebConfig::class.java, AuthController::class.java, GlobalExceptionHandler::class.java)
            .withBean(PermissionInterceptor::class.java, { PermissionInterceptor() })
            .withBean(AuthService::class.java, { service })
            .run { context ->
                assertNull(context.startupFailure)
                val builder = MockMvcBuilders.webAppContextSetup(context)
                context.getBeansOfType(FilterRegistrationBean::class.java).values.sortedBy { it.order }.forEach {
                    builder.addFilters<DefaultMockMvcBuilder>(it.filter as Filter)
                }
                builder.addFilters<DefaultMockMvcBuilder>(JwtAuthFilter(tokens, loader, jacksonObjectMapper()))
                check(builder.build())
            }
    }

    @Test
    fun `跨域登录后可以预检并读取身份和菜单`() = withMvc { mvc ->
        for (origin in listOf("http://localhost:5173", "http://127.0.0.1:5173", "http://[::1]:5173")) {
            mvc.perform(options("/api/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk).andExpect(header().string("Access-Control-Allow-Origin", origin))
            val login = mvc.perform(post("/api/auth/login").header("Origin", origin)
                .contentType("application/json").content("""{"username":"reader","password":"test-password"}"""))
                .andExpect(status().isOk).andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andReturn().response.contentAsString
            val token = jacksonObjectMapper().readTree(login).path("data").path("accessToken").asString()
            for (path in listOf("/api/auth/me", "/api/auth/routes")) {
                mvc.perform(options(path).header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "authorization"))
                    .andExpect(status().isOk).andExpect(header().string("Access-Control-Allow-Origin", origin))
                mvc.perform(get(path).header("Origin", origin).header("Authorization", "Bearer $token"))
                    .andExpect(status().isOk).andExpect(header().string("Access-Control-Allow-Origin", origin))
            }
        }
    }

    @Test
    fun `跨域请求没有有效身份仍返回浏览器可读取的401`() = withMvc { mvc ->
        val origin = "http://localhost:5173"
        mvc.perform(get("/api/auth/me").header("Origin", origin))
            .andExpect(status().isUnauthorized).andExpect(header().string("Access-Control-Allow-Origin", origin))
        mvc.perform(get("/api/auth/me").header("Origin", origin).header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized).andExpect(header().string("Access-Control-Allow-Origin", origin))
    }

    @Test
    fun `跨域预检仍拒绝未允许的来源方法和请求头`() = withMvc { mvc ->
        for ((origin, method, requestedHeader) in listOf(
            Triple("https://untrusted.example", "GET", "authorization"),
            Triple("http://localhost:5173", "TRACE", "authorization"),
            Triple("http://localhost:5173", "GET", "x-untrusted-header"),
        )) {
            mvc.perform(options("/api/auth/me").header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", requestedHeader))
                .andExpect(status().isForbidden)
        }
    }
}
