package com.atguigu.hrspring.config

import com.atguigu.hrspring.auth.PermissionInterceptor
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry
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

    override fun addCorsMappings(registry: CorsRegistry) {
        val allowed = properties.cors.allowedHosts
        val registration = registry.addMapping("/api/**")
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
            .allowedHeaders("Content-Type", "Authorization")
        when {
            allowed.contains("*") -> registration.allowedOriginPatterns("*")
            allowed.isEmpty() -> registration.allowedOriginPatterns(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*",
            )
            else -> {
                val origins = allowed.flatMap { originVariants(it) }
                val patterns = origins.filter { it.contains('*') }
                val exact = origins.filterNot { it.contains('*') }
                if (exact.isNotEmpty()) registration.allowedOrigins(*exact.toTypedArray())
                if (patterns.isNotEmpty()) registration.allowedOriginPatterns(*patterns.toTypedArray())
            }
        }
    }

    /** /api 响应统一加安全头。 */
    @Bean
    fun securityHeadersFilter(): FilterRegistrationBean<OncePerRequestFilter> {
        val filter = object : OncePerRequestFilter() {
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
            addUrlPatterns("/api/*")
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
