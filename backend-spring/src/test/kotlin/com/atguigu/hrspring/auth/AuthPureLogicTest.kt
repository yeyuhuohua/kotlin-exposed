package com.atguigu.hrspring.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordHasherTest {

    private val hasher = PasswordHasher()

    @Test
    fun `哈希可校验且每次盐不同`() {
        val a = hasher.hash("secret-123")
        val b = hasher.hash("secret-123")
        assertNotEquals(a, b)
        assertTrue(hasher.verify("secret-123", a))
        assertTrue(hasher.verify("secret-123", b))
        assertFalse(hasher.verify("secret-124", a))
    }

    @Test
    fun `格式非法或迭代数越界拒绝`() {
        assertFalse(hasher.verify("x", "not-a-hash"))
        assertFalse(hasher.verify("x", "pbkdf2-sha256$100\$c2FsdA==\$aGFzaA=="))
        assertFalse(hasher.verify("x", "pbkdf2-sha256\$2000000\$c2FsdA==\$aGFzaA=="))
    }
}

class LoginThrottleTest {

    private fun throttle(
        windowSeconds: Long = 300,
        maxAccount: Int = 8,
        maxAddress: Int = 30,
    ) = LoginThrottle(
        com.atguigu.hrspring.config.HrProperties(
            auth = com.atguigu.hrspring.config.HrProperties.AuthProperties(
                loginRateLimit = com.atguigu.hrspring.config.HrProperties.AuthProperties.LoginRateLimitProperties(
                    windowSeconds = windowSeconds,
                    maxAccountFailures = maxAccount,
                    maxAddressFailures = maxAddress,
                ),
            ),
        ),
    )

    @Test
    fun `账号维度达到上限后被限流`() {
        val t = throttle()
        val account = t.accountKey("admin")
        val address = t.addressKey("10.0.0.1")
        repeat(7) { t.recordFailure(account, address) }
        assertNull(t.blockedSeconds(account, address))
        t.recordFailure(account, address)
        val wait = t.blockedSeconds(account, address)
        assertTrue(wait != null && wait in 1..300)
    }

    @Test
    fun `IP 维度独立计数且成功只清账号`() {
        val t = throttle(maxAccount = 2, maxAddress = 2)
        val address = t.addressKey("10.0.0.2")
        repeat(2) {
            t.recordFailure(t.accountKey("user$it"), address)
        }
        assertTrue(t.blockedSeconds(t.accountKey("nobody"), address) != null)
        t.recordSuccess(t.accountKey("user0"))
        assertNull(t.blockedSeconds(t.accountKey("user0"), t.addressKey("10.0.0.9")))
        assertTrue(t.blockedSeconds(t.accountKey("user0"), address) != null)
    }
}

class PermissionCatalogTest {

    @Test
    fun `路径模板匹配参数段`() {
        val def = PermissionCatalog.requiredApi("GET", "/api/employees/100")
        assertEquals("api:GET:/api/employees/{id}", def?.code)
        assertEquals("api:PUT:/api/jobs/{id}", PermissionCatalog.requiredApi("PUT", "/api/jobs/AD_VP")?.code)
    }

    @Test
    fun `HEAD 归一化为 GET 且未登记路径拒绝`() {
        assertEquals("api:GET:/api/jobs", PermissionCatalog.requiredApi("HEAD", "/api/jobs")?.code)
        assertNull(PermissionCatalog.requiredApi("GET", "/api/unknown"))
        assertNull(PermissionCatalog.requiredApi("POST", "/api/jobs/1"))
    }

    @Test
    fun `默认授权 ADMIN 全量 READER 只读`() {
        val adminDefaults = PermissionCatalog.defaults(Roles.ADMIN)
        assertEquals(PermissionCatalog.definitions.size, adminDefaults.size)
        val readerDefaults = PermissionCatalog.defaults(Roles.READER)
        assertTrue(readerDefaults.all { it.startsWith("api:GET:") })
        assertTrue(readerDefaults.none { PermissionCatalog.byCode[it]?.adminOnly == true })
    }

    @Test
    fun `非 ADMIN 的 adminOnly 权限在生效集合中被过滤`() {
        val user = AuthUser(1, "u", "READER", enabled = true, roleEnabled = true, tokenVersion = 0,
            rolePermissions = setOf("api:GET:/api/auth/users", "page:custom-page", "api:GET:/api/jobs"))
        val effective = PermissionCatalog.effective(user)
        assertTrue("page:custom-page" in effective)
        assertTrue("api:GET:/api/jobs" in effective)
        assertFalse("api:GET:/api/auth/users" in effective)
    }
}
