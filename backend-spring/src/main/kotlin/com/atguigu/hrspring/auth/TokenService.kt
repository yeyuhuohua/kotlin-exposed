package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * JWT 签发与校验:HS256,claims 为 iss/aud/sub/ver/jti/iat/exp,
 * ver 对应 auth_users.token_version,用于改密/停用/改权限后的吊销。
 */
@Component
class TokenService(
    properties: HrProperties,
) {
    private val auth = properties.auth
    private val algorithm: Algorithm
    private val verifier: com.auth0.jwt.interfaces.JWTVerifier

    init {
        require(auth.jwtSecret.toByteArray().size >= 32) { "hr.auth.jwt-secret must be at least 32 bytes" }
        require(auth.ttlSeconds in 60..86400) { "hr.auth.ttl-seconds must be between 60 and 86400" }
        algorithm = Algorithm.HMAC256(auth.jwtSecret)
        verifier = JWT.require(algorithm)
            .withIssuer(auth.issuer)
            .withAudience(auth.audience)
            .build()
    }

    val ttlSeconds: Long get() = auth.ttlSeconds

    fun issue(userId: Int, tokenVersion: Int): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(auth.issuer)
            .withAudience(auth.audience)
            .withSubject(userId.toString())
            .withClaim("ver", tokenVersion)
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(auth.ttlSeconds))
            .sign(algorithm)
    }

    /**
     * 校验通过返回 (userId, tokenVersion);token 无效或缺少必要 claim 返回 null。
     */
    fun verify(token: String): Pair<Int, Int>? {
        val jwt: DecodedJWT = try {
            verifier.verify(token)
        } catch (e: Exception) {
            return null
        }
        val sub = jwt.subject ?: return null
        val userId = sub.toIntOrNull() ?: return null
        val ver = jwt.getClaim("ver").takeIf { !it.isNull }?.asInt() ?: return null
        if (jwt.issuedAt == null) return null
        return userId to ver
    }
}
