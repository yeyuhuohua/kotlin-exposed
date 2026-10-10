package com.atguigu.hrspring.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hr")
data class HrProperties(
    val cache: CacheProperties = CacheProperties(),
    val auth: AuthProperties = AuthProperties(),
    val security: SecurityProperties = SecurityProperties(),
    val cors: CorsProperties = CorsProperties(),
    val mcp: McpProperties = McpProperties(),
) {
    data class McpProperties(
        /** 本地 DSH 调用 /mcp 用的固定口令。为空则只接受管理员 JWT。 */
        val serviceToken: String = "",
    )

    data class CacheProperties(
        val ttlSeconds: Long = 600,
    )

    data class AuthProperties(
        val jwtSecret: String = "",
        val issuer: String = "hr-api",
        val audience: String = "hr-api-client",
        val ttlSeconds: Long = 3600,
        val initializeSchema: Boolean = true,
        val adminUsername: String = "admin",
        val adminPassword: String = "admin123456",
        val permissionCacheSeconds: Long = 3,
        val loginRateLimit: LoginRateLimitProperties = LoginRateLimitProperties(),
    ) {
        data class LoginRateLimitProperties(
            val enabled: Boolean = true,
            val windowSeconds: Long = 300,
            val maxAccountFailures: Int = 8,
            val maxAddressFailures: Int = 30,
        )
    }

    data class SecurityProperties(
        val trustedProxies: List<String> = emptyList(),
    )

    data class CorsProperties(
        val allowedHosts: List<String> = emptyList(),
    )
}
