package com.atguigu.hrspring.config

import com.atguigu.hrspring.auth.PermissionInterceptor
import com.atguigu.hrspring.common.api.applicationPath
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.filter.CorsFilter
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableConfigurationProperties(HrProperties::class)
class WebConfig(
    private val properties: HrProperties,
    private val permissionInterceptor: PermissionInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**")
    }

    /** 在鉴权之前完成预检，并让 401 等提前返回的响应也带上跨域头。 */
    @Bean
    fun corsFilter(): FilterRegistrationBean<CorsFilter> {
        val allowed = properties.cors.allowedHosts
        val cors = CorsConfiguration().apply {
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
            allowedHeaders = listOf("Content-Type", "Authorization")
            maxAge = 1800L
        }
        when {
            allowed.contains("*") -> cors.allowedOriginPatterns = listOf("*")
            allowed.isEmpty() -> cors.allowedOriginPatterns = listOf(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*",
            )
            else -> {
                val origins = allowed.flatMap { originVariants(it) }
                val patterns = origins.filter { it.contains('*') }
                val exact = origins.filterNot { it.contains('*') }
                if (exact.isNotEmpty()) cors.allowedOrigins = exact
                if (patterns.isNotEmpty()) cors.allowedOriginPatterns = patterns
            }
        }
        val filter = CorsFilter { request -> cors.takeIf { request.applicationPath().startsWith("/api/") } }
        return FilterRegistrationBean(filter).apply {
            addUrlPatterns("/*")
            order = Integer.MIN_VALUE + 1
        }
    }

    /** /api 响应统一加安全头。 */
    @Bean
    fun securityHeadersFilter(): FilterRegistrationBean<OncePerRequestFilter> {
        val filter = object : OncePerRequestFilter() {
            override fun shouldNotFilter(request: HttpServletRequest): Boolean =
                !request.applicationPath().startsWith("/api/")

            override fun doFilterInternal(
                request: HttpServletRequest,
                response: HttpServletResponse,
                chain: FilterChain,
            ) {
                response.setHeader("X-Content-Type-Options", "nosniff")
                response.setHeader("X-Frame-Options", "DENY")
                response.setHeader("Referrer-Policy", "no-referrer")
                chain.doFilter(request, response)
            }
        }
        return FilterRegistrationBean<OncePerRequestFilter>(filter).apply {
            addUrlPatterns("/*")
            order = Integer.MIN_VALUE
        }
    }

    /** 已带协议的配置按完整 Origin 使用；只有主机时同时放开 http 和 https。 */
    private fun originVariants(value: String): List<String> {
        val origin = value.trim()
        if (origin.contains("://")) return listOf(origin)
        return listOf("http://$origin", "https://$origin")
    }
}
