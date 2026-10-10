package com.atguigu.hrspring.audit

import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.common.api.ClientIp
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.OncePerRequestFilter
import java.time.LocalDateTime

/**
 * 接口调用审计:响应完成后记录(状态码为最终结果),一次请求只记一条。
 * 只记 /api/ 开头且排除 /api/health;未认证请求的 userId/username 为 null。
 */
@Configuration
class AuditFilterConfig(
    private val auditService: AuditService,
    private val clientIp: ClientIp,
) {
    @Bean
    fun auditFilter(): FilterRegistrationBean<OncePerRequestFilter> {
        val filter = object : OncePerRequestFilter() {
            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                chain: FilterChain,
            ) {
                val started = System.nanoTime()
                try {
                    chain.doFilter(request, response)
                } finally {
                    runCatching {
                        val user = CurrentUserHolder.get()
                        auditService.recordApiCall(
                            userId = user?.id,
                            username = user?.username,
                            method = request.method,
                            path = request.requestURI,
                            queryString = request.queryString?.takeIf { it.isNotBlank() },
                            statusCode = response.status,
                            durationMs = (System.nanoTime() - started) / 1_000_000,
                            ip = clientIp.resolve(request),
                            userAgent = request.getHeader("User-Agent"),
                            occurredAt = LocalDateTime.now(),
                        )
                    }
                }
            }
        }
        return FilterRegistrationBean<OncePerRequestFilter>(filter).apply {
            addUrlPatterns("/api/*")
            order = Int.MAX_VALUE
        }
    }
}
