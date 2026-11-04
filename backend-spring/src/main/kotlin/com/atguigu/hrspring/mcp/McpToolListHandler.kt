package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import tools.jackson.databind.ObjectMapper
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.spec.McpSchema
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

/** 按当前用户过滤工具列表；其他请求保留请求体并交回 Spring AI。 */
@Component
class McpToolListHandler(
    private val objectMapper: ObjectMapper,
    private val mcpServer: ObjectProvider<McpSyncServer>,
) {
    /** 返回 null 表示已直接响应工具列表，否则返回可继续读取请求体的请求。 */
    fun handle(request: HttpServletRequest, response: HttpServletResponse, user: AuthUser): HttpServletRequest? {
        if (request.method != "POST") return request
        val body = request.inputStream.readBytes()
        val downstream = CachedBodyRequest(request, body)
        val node = runCatching { objectMapper.readTree(body) }.getOrNull() ?: return downstream
        if (!node.isObject || node.get("method")?.textValue() != "tools/list") return downstream

        val tools = mcpServer.getIfAvailable()?.listTools().orEmpty()
            .filter { McpToolPermissions.isAllowed(it.name(), user) }
        val result = McpSchema.ListToolsResult.builder(tools).build()
        response.status = 200
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-store")
        response.writer.write(
            objectMapper.writeValueAsString(
                mapOf("jsonrpc" to "2.0", "id" to node.get("id"), "result" to result),
            ),
        )
        return null
    }
}
