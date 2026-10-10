package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TokenServiceTest {

    private fun service(
        jwtSecret: String = "a".repeat(32),
        issuer: String = "hr-api",
        audience: String = "hr-api-client",
        ttlSeconds: Long = 3600,
    ) = TokenService(
        HrProperties(
            auth = HrProperties.AuthProperties(
                jwtSecret = jwtSecret,
                issuer = issuer,
                audience = audience,
                ttlSeconds = ttlSeconds,
            ),
        ),
    )

    @Test
    fun `签发后校验还原用户与版本且互不混淆`() {
        val s = service()
        assertEquals(7 to 3, s.verify(s.issue(7, 3)))
        assertEquals(8 to 4, s.verify(s.issue(8, 4)))
        assertEquals(8 to 3, s.verify(s.issue(8, 3)))
    }

    @Test
    fun `密钥签发方受众不匹配的 token 一律拒绝`() {
        val s = service()
        assertNull(s.verify(service(jwtSecret = "b".repeat(32)).issue(7, 3)))
        assertNull(s.verify(service(issuer = "other-issuer").issue(7, 3)))
        assertNull(s.verify(service(audience = "other-audience").issue(7, 3)))
        assertNull(s.verify("not-a-jwt"))
        assertNull(s.verify(""))
    }

    @Test
    fun `jwtSecret 不足 32 字节拒绝启动`() {
        assertFailsWith<IllegalArgumentException> { service(jwtSecret = "a".repeat(31)) }
        service(jwtSecret = "a".repeat(32))
    }

    @Test
    fun `ttlSeconds 超出 60 到 86400 拒绝启动`() {
        assertFailsWith<IllegalArgumentException> { service(ttlSeconds = 59) }
        assertFailsWith<IllegalArgumentException> { service(ttlSeconds = 86401) }
        service(ttlSeconds = 60)
        service(ttlSeconds = 86400)
    }
}
