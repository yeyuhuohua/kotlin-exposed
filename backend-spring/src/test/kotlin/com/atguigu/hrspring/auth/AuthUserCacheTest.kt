package com.atguigu.hrspring.auth

import com.atguigu.hrspring.config.HrProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUserCacheTest {

    private fun cache(permissionCacheSeconds: Long = 3) = AuthUserCache(
        HrProperties(
            auth = HrProperties.AuthProperties(permissionCacheSeconds = permissionCacheSeconds),
        ),
    )

    private fun user(id: Int, tokenVersion: Int = 0) = AuthUser(
        id,
        "user$id",
        "READER",
        enabled = true,
        roleEnabled = true,
        tokenVersion = tokenVersion,
        rolePermissions = emptySet(),
    )

    @Test
    fun `读库后代次变化的旧回填被拒绝`() {
        val c = cache()
        val staleGeneration = c.generation()
        c.invalidateUser(1)
        c.put(1, 0, user(1), staleGeneration)
        assertNull(c.get(1, 0))

        val beforeInvalidateAll = c.generation()
        c.invalidateAll()
        c.put(2, 0, user(2), beforeInvalidateAll)
        assertNull(c.get(2, 0))
    }

    @Test
    fun `invalidateUser 只清指定用户的条目`() {
        val c = cache()
        val gen = c.generation()
        c.put(1, 0, user(1), gen)
        c.put(2, 0, user(2), gen)
        c.put(12, 0, user(12), gen)
        c.invalidateUser(1)
        assertNull(c.get(1, 0))
        assertEquals(2, c.get(2, 0)?.id)
        assertEquals(12, c.get(12, 0)?.id)
    }

    @Test
    fun `permissionCacheSeconds 为 0 时缓存停用`() {
        val c = cache(permissionCacheSeconds = 0)
        assertFalse(c.enabled)
        c.put(1, 0, user(1), c.generation())
        assertNull(c.get(1, 0))
    }

    @Test
    fun `同代次回填后可正常命中`() {
        val c = cache()
        assertTrue(c.enabled)
        val gen = c.generation()
        c.put(1, 0, user(1), gen)
        c.put(1, 1, user(1, tokenVersion = 1), gen)
        assertEquals(1, c.get(1, 0)?.id)
        assertEquals(1, c.get(1, 1)?.tokenVersion)
        assertNull(c.get(1, 2))
    }
}
