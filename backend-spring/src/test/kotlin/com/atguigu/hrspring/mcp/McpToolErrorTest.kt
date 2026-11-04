package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.common.api.ApiException
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import org.mockito.Mockito
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

open class ThrowingHealthTools(private val action: () -> String) {
    @McpTool(name = "get_health", description = "测试异常处理")
    open fun getHealth(context: McpSyncRequestContext): String = action()
}

class McpToolErrorTest {
    @Test
    fun `未知异常只返回通用错误并清理身份`() {
        val result = execute { throw IllegalStateException("database password=must-not-leak") }
        val text = textOf(result)
        assertTrue(result.isError)
        assertEquals(setOf("internal error"), text.lines().toSet())
        assertFalse("must-not-leak" in text)
        assertNull(CurrentUserHolder.get())
    }

    @Test
    fun `已知业务错误保留简洁提示`() {
        val result = execute { throw ApiException.badRequest("salary must be non-negative") }
        assertTrue(result.isError)
        assertEquals(setOf("salary must be non-negative"), textOf(result).lines().toSet())
    }

    @Test
    fun `参数错误保留原始说明`() {
        val result = execute { throw IllegalArgumentException("limit must be between 1 and 200") }
        assertTrue(result.isError)
        assertEquals(setOf("limit must be between 1 and 200"), textOf(result).lines().toSet())
    }

    private fun execute(action: () -> String): McpSchema.CallToolResult {
        val user = AuthUser(2, "reader", Roles.READER, true, true, 0, emptySet())
        val proxy = AspectJProxyFactory(ThrowingHealthTools(action)).apply {
            addAspect(McpToolAuthorizationAspect())
        }.getProxy() as ThrowingHealthTools
        val specification = SyncMcpAnnotationProviders.toolSpecifications(listOf(proxy)).single()
        val exchange = Mockito.mock(McpSyncServerExchange::class.java)
        Mockito.`when`(exchange.transportContext()).thenReturn(
            McpTransportContext.create(mapOf(McpSecurityConfig.MCP_AUTHENTICATED_USER to user)),
        )
        val request = McpSchema.CallToolRequest.builder().name("get_health").arguments(emptyMap<String, Any>()).build()
        return specification.callHandler().apply(exchange, request)
    }

    private fun textOf(result: McpSchema.CallToolResult): String =
        result.content().filterIsInstance<McpSchema.TextContent>().joinToString("\n") { it.text() }
}
