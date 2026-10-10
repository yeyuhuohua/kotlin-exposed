package com.atguigu.hrspring.auth

import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.ErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JWT 鉴权过滤器:/api 下除公开路径外,携带无效 token 直接 401;
 * 未携带 token 放行给权限拦截器决定(同样 401),保证一处响应一处审计。
 */
@Component
class JwtAuthFilter(
    private val tokenService: TokenService,
    private val userLoader: AuthUserLoader,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        if (!path.startsWith("/api/")) return true
        return path == "/api/health" || path == "/api/auth/login"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        try {
            val header = request.getHeader("Authorization")
            val token = header?.takeIf { it.startsWith("Bearer ") }?.substring(7)?.trim()
            if (token != null) {
                val claims = tokenService.verify(token)
                val user = claims?.let { (id, ver) -> userLoader.loadActiveUser(id, ver) }
                if (claims == null || user == null) {
                    writeUnauthorized(response)
                    return
                }
                CurrentUserHolder.set(user)
            }
            chain.doFilter(request, response)
        } finally {
            CurrentUserHolder.clear()
        }
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = 401
        response.setHeader("WWW-Authenticate", "Bearer realm=\"hr-api\"")
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ApiResult<Nothing>(401, "authentication required", null, ErrorCode.UNAUTHORIZED)
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
