package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.AuthUserLoader
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.JwtAuthFilter
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.auth.TokenService
import com.atguigu.hrspring.config.HrProperties
import java.security.MessageDigest
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.common.api.applicationPath
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * MCP 端点只允许 ADMIN 用户的有效 JWT 或服务口令访问。
 * 通过后把用户放进 CurrentUserHolder，工具直接调服务，不再逐接口鉴权。
 */
@Configuration
@EnableConfigurationProperties(McpServerStreamableHttpProperties::class)
class McpSecurityConfig(
    private val tokenService: TokenService,
    private val userLoader: AuthUserLoader,
    private val properties: HrProperties,
    private val objectMapper: ObjectMapper,
    private val mcpProperties: McpServerStreamableHttpProperties,
) {

    @Bean
    fun mcpSecurityFilter(): FilterRegistrationBean<OncePerRequestFilter> {
        val endpoint = mcpProperties.mcpEndpoint
        val filter = object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest): Boolean {
                val path = request.applicationPath()
                return path != endpoint && !path.startsWith("${endpoint.trimEnd('/')}/")
            }

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
                if (user == null || user.roleCode != Roles.ADMIN) {
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
                    chain.doFilter(request, response)
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

    private fun resolveUser(token: String?): AuthUser? {
        if (token.isNullOrBlank()) return null
        val serviceToken = properties.mcp.serviceToken
        if (serviceToken.isNotBlank() && MessageDigest.isEqual(serviceToken.toByteArray(), token.toByteArray())) {
            return userLoader.loadActiveAdmin()
        }
        val claims = tokenService.verify(token) ?: return null
        return userLoader.loadActiveUser(claims.first, claims.second)
    }

    companion object {
        const val AUTHENTICATED_REQUEST = "hrs.mcpAuthenticated"
    }
}
