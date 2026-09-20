package com.atguigu.hr.auth

import io.ktor.server.config.ApplicationConfig

/** 读取并校验 JWT 配置；缺失密钥或不合法的有效期必须在服务启动时失败。 */

data class AuthSettings(
    val secret: String,
    val issuer: String = "hr-api",
    val audience: String = "hr-api-client",
    val ttlSeconds: Long = 3600,
) {
    init {
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) { "auth.jwtSecret must contain at least 32 bytes" }
        require(issuer.isNotBlank() && audience.isNotBlank()) { "JWT issuer and audience must not be blank" }
        require(ttlSeconds in 60..86_400) { "JWT ttlSeconds must be between 60 and 86400" }
    }

    companion object {
        fun from(config: ApplicationConfig) = AuthSettings(
            secret = config.propertyOrNull("auth.jwtSecret")?.getString()?.takeIf { it.isNotBlank() }
                ?: error("Set auth.jwtSecret in application.yaml to a random secret of at least 32 bytes"),
            issuer = config.propertyOrNull("auth.issuer")?.getString() ?: "hr-api",
            audience = config.propertyOrNull("auth.audience")?.getString() ?: "hr-api-client",
            ttlSeconds = config.propertyOrNull("auth.ttlSeconds")?.getString()?.toLong() ?: 3600,
        )
    }
}
