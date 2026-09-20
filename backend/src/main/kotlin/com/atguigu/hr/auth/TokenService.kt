package com.atguigu.hr.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.UUID

/** JWT 只携带用户标识和会话版本，实际角色及权限仍在每次请求时从数据库校验。 */

class TokenService(private val settings: AuthSettings) {
    private val algorithm = Algorithm.HMAC256(settings.secret)
    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(settings.issuer)
        .withAudience(settings.audience)
        .withClaimPresence("exp")
        .withClaimPresence("iat")
        .withClaimPresence("sub")
        .withClaimPresence("ver")
        .build()

    fun issue(user: AuthUser): TokenDto {
        val now = Instant.now()
        val token = JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withSubject(user.id.toString())
            .withClaim("ver", user.tokenVersion)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(settings.ttlSeconds))
            .sign(algorithm)
        return TokenDto(token, settings.ttlSeconds, user.toCurrentUser())
    }
}
