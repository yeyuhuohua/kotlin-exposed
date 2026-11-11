package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.AuthUserLoader
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.JwtAuthFilter
import com.atguigu.hrspring.auth.TokenService
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.common.api.applicationPath
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import io.modelcontextprotocol.common.McpTransportContext
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * MCP 端点接受所有有效用户的 JWT，并保留当前用户身份。
 * 工具列表按用户过滤，工具执行前再由 McpToolAuthorizationAspect 校验。
 */
@Configuration
@EnableConfigurationProperties(McpServerStreamableHttpProperties::class)
class McpSecurityConfig(
    private val tokenService: TokenService,
    private val userLoader: AuthUserLoader,
    private val objectMapper: ObjectMapper,
    private val mcpProperties: McpServerStreamableHttpProperties,
    private val toolListHandler: McpToolListHandler,
) {

    /** 创建 MCP HTTP 传输，并把请求中的认证用户写入协议上下文。 */
    @Bean
    fun webMvcStreamableServerTransportProvider(
        @Qualifier("mcpServerJsonMapper") jsonMapper: JsonMapper,
    ): WebMvcStreamableServerTransportProvider = WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(mcpProperties.mcpEndpoint)
        .keepAliveInterval(mcpProperties.keepAliveInterval)
        .disallowDelete(mcpProperties.isDisallowDelete)
        .contextExtractor { request ->
            val user = request.servletRequest().getAttribute(JwtAuthFilter.AUTHENTICATED_USER) as? AuthUser
            if (user == null) McpTransportContext.EMPTY
            else McpTransportContext.create(mapOf(MCP_AUTHENTICATED_USER to user))
        }
        .build()

    /** 为 MCP 端点注册独立 JWT 过滤器，完成认证、列表过滤和上下文清理。 */
    @Bean
    fun mcpSecurityFilter(): FilterRegistrationBean<OncePerRequestFilter> {
        val endpoint = mcpProperties.mcpEndpoint
        val filter = object : OncePerRequestFilter() {
            /** 只让 MCP 端点及其子路径进入该过滤器。 */
            override fun shouldNotFilter(request: HttpServletRequest): Boolean {
                val path = request.applicationPath()
                return path != endpoint && !path.startsWith("${endpoint.trimEnd('/')}/")
            }

            /** 验证当前用户，处理工具列表或在协议上下文中传递身份后继续请求。 */
            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                chain: FilterChain,
            ) {
                val token = request.getHeader("Authorization")
                    ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                    ?.substring(7)
                    ?.trim()
                val user = resolveUser(token)
                if (user == null) {
                    response.status = 401
                    response.setHeader("WWW-Authenticate", "Bearer realm=\"hr-api\"")
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = "UTF-8"
                    response.writer.write(
                        objectMapper.writeValueAsString(
                            ApiResult<Nothing>(401, "authentication required", null, ErrorCode.UNAUTHORIZED),
                        ),
                    )
                    return
                }
                try {
                    CurrentUserHolder.set(user)
                    request.setAttribute(JwtAuthFilter.AUTHENTICATED_USER, user)
                    if (request.applicationPath() == endpoint) {
                        request.setAttribute(AUTHENTICATED_REQUEST, true)
                    }
                    val downstream = toolListHandler.handle(request, response, user) ?: return
                    chain.doFilter(downstream, response)
                } finally {
                    request.removeAttribute(AUTHENTICATED_REQUEST)
                    CurrentUserHolder.clear()
                }
            }
        }
        return FilterRegistrationBean<OncePerRequestFilter>(filter).apply {
            // Servlet 过滤器按原始路径匹配，端点判断需在过滤器内去掉 MVC 的映射前缀。
            addUrlPatterns("/*")
            order = 0
        }
    }

    /** 验证 JWT 并加载仍然有效的用户及最新角色权限。 */
    private fun resolveUser(token: String?): AuthUser? {
        if (token.isNullOrBlank()) return null
        val claims = tokenService.verify(token) ?: return null
        return userLoader.loadActiveUser(claims.first, claims.second)
    }

    companion object {
        const val AUTHENTICATED_REQUEST = "hrs.mcpAuthenticated"
        const val MCP_AUTHENTICATED_USER = "hrs.mcp.user"
    }
}
