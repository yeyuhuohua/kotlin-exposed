package com.atguigu.hr.auth

import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.util.UUID

/** 完成认证表和初始管理员的幂等初始化，并准备登录校验需要的服务依赖。 */

suspend fun createAuthService(config: ApplicationConfig, database: R2dbcDatabase, tokens: TokenService): AuthService {
    val repository = AuthRepository(database)
    val username = config.propertyOrNull("auth.adminUsername")?.getString()?.takeIf { it.isNotBlank() }
    val password = config.propertyOrNull("auth.adminPassword")?.getString()?.takeIf { it.isNotBlank() }
    require((username == null) == (password == null)) {
        "Set both auth.adminUsername and auth.adminPassword in application.yaml, or neither"
    }
    val normalizedUsername = username?.let(AuthService::normalizeUsername)
    password?.let(AuthService::validatePassword)
    if (config.propertyOrNull("auth.initializeSchema")?.getString()?.toBooleanStrict() != false) {
        repository.initialize()
    }
    if (normalizedUsername != null && password != null) {
        repository.bootstrapAdmin(normalizedUsername, PasswordHasher.hash(password))
    }
    val cacheSeconds = config.propertyOrNull("auth.permissionCacheSeconds")?.getString()?.toLongOrNull() ?: 3
    return AuthService(
        repository,
        tokens,
        PasswordHasher.hash(UUID.randomUUID().toString()),
        AuthUserCache(ttlMillis = cacheSeconds.coerceAtLeast(0) * 1000),
    )
}
