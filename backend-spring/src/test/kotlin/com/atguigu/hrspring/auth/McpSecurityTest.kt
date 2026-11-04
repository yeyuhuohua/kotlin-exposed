package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import com.atguigu.hrspring.mcp.McpSecurityConfig
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import io.modelcontextprotocol.server.McpServer
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import io.modelcontextprotocol.server.McpServerFeatures
import io.modelcontextprotocol.spec.McpSchema
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.RouterFunctionMockMvcBuilder
import tools.jackson.databind.json.JsonMapper
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpSecurityTest {
    private val properties = HrProperties(auth = HrProperties.AuthProperties(jwtSecret = "a".repeat(32)))
    private val tokens = TokenService(properties)
    private val admin = AuthUser(7, "operator", Roles.ADMIN, true, true, 0, emptySet())
    private val reader = admin.copy(
        id = 8, username = "reader", roleCode = Roles.READER,
        rolePermissions = setOf("api:GET:/api/employees"),
    )
    private val loader = object : AuthUserLoader {
        override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? =
            listOf(admin, reader).find { it.id == userId && it.tokenVersion == tokenVersion }
    }
    private val initialize = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""

    private fun withServer(
        endpoint: String,
        tools: List<McpServerFeatures.SyncToolSpecification> = emptyList(),
        check: (MockMvc) -> Unit,
    ) {
        ApplicationContextRunner()
            .withUserConfiguration(McpSecurityConfig::class.java, com.atguigu.hrspring.mcp.McpToolListHandler::class.java)
            .withPropertyValues("spring.ai.mcp.server.streamable-http.mcp-endpoint=$endpoint")
            .withBean(HrProperties::class.java, Supplier { properties })
            .withBean(TokenService::class.java, Supplier { tokens })
            .withBean(AuthUserLoader::class.java, Supplier { loader })
            .withBean(ObjectMapper::class.java, Supplier { jacksonObjectMapper() })
            .withBean("mcpServerJsonMapper", JsonMapper::class.java, Supplier { JsonMapper.builder().build() })
            .run { context ->
                assertNull(context.startupFailure)
                val provider = context.getBean(WebMvcStreamableServerTransportProvider::class.java)
                val builder = McpServer.sync(provider).serverInfo("test", "1")
                if (tools.isNotEmpty()) builder.tools(tools)
                val server = builder.build()
                context.beanFactory.registerSingleton("mcpSyncServer", server)
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
    fun `自定义MCP端点拒绝无效JWT并接受普通用户`() = withServer("/tools") { mvc ->
        mvc.perform(post("/tools").header("Authorization", "Bearer invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/tools").header("Authorization", "Bearer ${tokens.issue(reader.id, reader.tokenVersion)}")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
            .andExpect(status().isOk)
    }

    @Test
    fun `MCP接受用户JWT并拒绝固定服务口令`() = withServer("/tools") { mvc ->
        mvc.perform(post("/hr/tools").contextPath("/hr").header("Authorization", "Bearer ${tokens.issue(admin.id, admin.tokenVersion)}")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
            .andExpect(status().isOk)
        mvc.perform(post("/hr/tools").contextPath("/hr").header("Authorization", "Bearer fixed-service-token")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `MCP的Bearer方案忽略大小写且仍拒绝其他方案`() = withServer("/api/mcp") { mvc ->
        for (user in listOf(admin, reader)) {
            for (scheme in listOf("Bearer", "bearer", "bEaReR")) {
                mvc.perform(post("/api/mcp").header("Authorization", "$scheme ${tokens.issue(user.id, user.tokenVersion)}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                    .andExpect(status().isOk)
            }
        }
        mvc.perform(post("/api/mcp").header("Authorization", "Basic ${tokens.issue(admin.id, admin.tokenVersion)}")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `API前缀下的MCP接受管理员和普通用户JWT`() = withServer("/api/mcp") { mvc ->
        for (user in listOf(admin, reader)) {
            mvc.perform(post("/hr/api/mcp").contextPath("/hr")
                .header("Authorization", "Bearer ${tokens.issue(user.id, user.tokenVersion)}")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize))
                .andExpect(status().isOk)
        }
        for (token in listOf("invalid", "fixed-service-token")) {
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
        mvc.perform(request().header("Authorization", "Bearer ${tokens.issue(reader.id, reader.tokenVersion)}"))
            .andExpect(status().isOk)
        mvc.perform(get("/unrelated")).andExpect(status().isNotFound)
    }

    @Test
    fun `工具列表按当前用户权限过滤`() = withServer(
        "/mcp",
        listOf(tool("list_employees"), tool("create_employee")),
    ) { mvc ->
        val body = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""
        mvc.perform(post("/mcp").header("Authorization", "Bearer ${tokens.issue(reader.id, reader.tokenVersion)}")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("result.tools.length()").value(1))
            .andExpect(jsonPath("result.tools[0].name").value("list_employees"))
        mvc.perform(post("/mcp").header("Authorization", "Bearer ${tokens.issue(admin.id, admin.tokenVersion)}")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk)
            .andExpect(jsonPath("result.tools.length()").value(2))
    }

    @Test
    fun `工具列表过滤不依赖ContentLength`() = withServer(
        "/mcp",
        listOf(tool("list_employees"), tool("create_employee")),
    ) { mvc ->
        for (unknownLength in listOf(true, false)) {
            val padding = if (unknownLength) "" else "x".repeat(70_000)
            val body = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{},"padding":"$padding"}"""
            val request = post("/mcp")
                .header("Authorization", "Bearer ${tokens.issue(reader.id, reader.tokenVersion)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
            if (unknownLength) request.with { original ->
                org.mockito.Mockito.spy(original).also {
                    org.mockito.Mockito.doReturn(-1L).`when`(it).contentLengthLong
                }
            }
            mvc.perform(request)
                .andExpect(status().isOk)
                .andExpect(jsonPath("result.tools.length()").value(1))
                .andExpect(jsonPath("result.tools[0].name").value("list_employees"))
        }
    }

    @Test
    fun `真实工具调用在回调线程恢复原用户`() {
        val target = DummyHealthTools()
        val proxy = AspectJProxyFactory(target).apply {
            addAspect(com.atguigu.hrspring.mcp.McpToolAuthorizationAspect())
        }.getProxy() as DummyHealthTools
        val tools = SyncMcpAnnotationProviders.toolSpecifications(listOf(proxy))
        withServer("/mcp", tools) { mvc ->
            val authorization = "Bearer ${tokens.issue(reader.id, reader.tokenVersion)}"
            val session = mvc.perform(
                post("/mcp").header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .content(initialize),
            ).andExpect(status().isOk)
                .andReturn().response.getHeader("Mcp-Session-Id").let(::requireNotNull)
            val call = """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_health","arguments":{}}}"""
            val response = mvc.perform(
                post("/mcp").header("Authorization", authorization).header("Mcp-Session-Id", session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .content(call),
            ).andExpect(status().isOk).andReturn().response.contentAsString
            assertEquals(1, target.calls, response)
            assertTrue("\"text\":\"reader\"" in response, response)
            assertFalse("\"isError\":true" in response, response)
        }
    }

    private fun tool(name: String): McpServerFeatures.SyncToolSpecification =
        McpServerFeatures.SyncToolSpecification.builder()
            .tool(McpSchema.Tool.builder(name).build())
            .callHandler { _, _ -> McpSchema.CallToolResult.builder().content(emptyList()).build() }
            .build()
}

open class DummyHealthTools {
    var calls = 0

    @McpTool(name = "get_health", description = "测试跨线程身份")
    open fun getHealth(context: McpSyncRequestContext): String {
        calls++
        return CurrentUserHolder.require().username
    }
}
