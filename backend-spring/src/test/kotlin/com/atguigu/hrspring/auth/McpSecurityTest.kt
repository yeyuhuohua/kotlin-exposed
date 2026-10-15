package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.mcp.McpSecurityConfig
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServer
import jakarta.servlet.http.MappingMatch
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletMapping
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.RouterFunctionMockMvcBuilder
import tools.jackson.databind.json.JsonMapper
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertNull

class McpSecurityTest {
    private val properties = HrProperties(
        auth = HrProperties.AuthProperties(jwtSecret = "a".repeat(32)),
        mcp = HrProperties.McpProperties(serviceToken = "fixed-service-token"),
    )
    private val tokens = TokenService(properties)
    private val admin = AuthUser(7, "operator", Roles.ADMIN, true, true, 0, emptySet())
    private val reader = admin.copy(id = 8, username = "reader", roleCode = Roles.READER)
    private val loader = object : AuthUserLoader {
        override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? =
            listOf(admin, reader).find { it.id == userId && it.tokenVersion == tokenVersion }
        override fun loadActiveAdmin(): AuthUser = admin
    }
    private val initialize = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""

    private fun withServer(endpoint: String, check: (MockMvc) -> Unit) {
        ApplicationContextRunner()
            .withUserConfiguration(McpSecurityConfig::class.java)
            .withPropertyValues("spring.ai.mcp.server.streamable-http.mcp-endpoint=$endpoint")
            .withBean(HrProperties::class.java, Supplier { properties })
            .withBean(TokenService::class.java, Supplier { tokens })
            .withBean(AuthUserLoader::class.java, Supplier { loader })
            .withBean(ObjectMapper::class.java, Supplier { jacksonObjectMapper() })
            .run { context ->
                assertNull(context.startupFailure)
                val configured = context.getBean(McpServerStreamableHttpProperties::class.java)
                val provider = WebMvcStreamableServerTransportProvider.builder()
                    .jsonMapper(JacksonMcpJsonMapper(JsonMapper.builder().build()))
                    .mcpEndpoint(configured.mcpEndpoint)
                    .build()
                val server = McpServer.sync(provider).serverInfo("test", "1").build()
                try {
                    val registration = context.getBean("mcpSecurityFilter", FilterRegistrationBean::class.java)
                    val mvc = MockMvcBuilders.routerFunctions(provider.routerFunction)
                        .addInterceptors(PermissionInterceptor())
                        .addFilter<RouterFunctionMockMvcBuilder>(
                            requireNotNull(registration.filter), *registration.urlPatterns.toTypedArray(),
                        ).addFilters<RouterFunctionMockMvcBuilder>(JwtAuthFilter(tokens, loader, jacksonObjectMapper()))
                        .build()
                    check(mvc)
                } finally {
                    server.closeGracefully()
                }
                assertNull(CurrentUserHolder.get())
            }
    }

    @Test
    fun `默认及自定义MCP端点的所有方法都要求认证`() {
        for (endpoint in listOf("/mcp", "/tools", "/tools/hr")) {
            withServer(endpoint) { mvc ->
                mvc.perform(get(endpoint)).andExpect(status().isUnauthorized)
                mvc.perform(delete(endpoint)).andExpect(status().isUnauthorized)
                mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                    .andExpect(status().isUnauthorized)
            }
        }
    }

    @Test
    fun `自定义MCP端点拒绝无效JWT和普通用户`() = withServer("/tools") { mvc ->
        for (token in listOf("invalid", tokens.issue(reader.id, reader.tokenVersion))) {
            mvc.perform(post("/tools").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `自定义MCP端点接受管理员JWT及服务口令`() = withServer("/tools") { mvc ->
        for (token in listOf(tokens.issue(admin.id, admin.tokenVersion), properties.mcp.serviceToken)) {
            mvc.perform(post("/hr/tools").contextPath("/hr").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                .andExpect(status().isOk)
        }
    }

    @Test
    fun `MCP的Bearer方案忽略大小写且仍拒绝其他方案`() = withServer("/api/mcp") { mvc ->
        for (token in listOf(properties.mcp.serviceToken, tokens.issue(admin.id, admin.tokenVersion))) {
            for (scheme in listOf("Bearer", "bearer", "bEaReR")) {
                mvc.perform(post("/api/mcp").header("Authorization", "$scheme $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                    .andExpect(status().isOk)
            }
            mvc.perform(post("/api/mcp").header("Authorization", "Basic $token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                .andExpect(status().isUnauthorized)
        }
    }

    @Test
    fun `API前缀下的MCP仍接受管理员JWT及服务口令`() = withServer("/api/mcp") { mvc ->
        for (token in listOf(properties.mcp.serviceToken, tokens.issue(admin.id, admin.tokenVersion))) {
            mvc.perform(post("/hr/api/mcp").contextPath("/hr").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                .andExpect(status().isOk)
        }
        for (token in listOf("invalid", tokens.issue(reader.id, reader.tokenVersion))) {
            mvc.perform(post("/hr/api/mcp").contextPath("/hr").header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                .andExpect(status().isUnauthorized)
        }
        mvc.perform(post("/hr/api/mcp").contextPath("/hr")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `MCP部署及servlet映射前缀不影响鉴权`() = withServer("/tools") { mvc ->
        fun request() = post("/hr/gateway/tools").contextPath("/hr").servletPath("/gateway")
            .pathInfo("/tools").with { request ->
                request.setHttpServletMapping(
                    MockHttpServletMapping("tools", "/gateway/*", "dispatcherServlet", MappingMatch.PATH),
                )
                request
            }.contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize)
        mvc.perform(request()).andExpect(status().isUnauthorized)
        mvc.perform(request().header("Authorization", "Bearer ${properties.mcp.serviceToken}"))
            .andExpect(status().isOk)
        mvc.perform(get("/unrelated")).andExpect(status().isNotFound)
    }
}
