package com.atguigu.hr.auth

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectedAccountTest {
    @Test
    fun `内置及配置指定的管理员都受保护`() {
        assertTrue(protectedUsername("admin", "operator"))
        assertTrue(protectedUsername("Operator", "operator"))
        assertFalse(protectedUsername("reader", "operator"))
    }

    @Test
    fun `用户响应包含保护标记`() {
        val body = Json.encodeToString(UserDto(1, "operator", "ADMIN", true, true))
        assertTrue("\"protected\":true" in body)
    }
}
