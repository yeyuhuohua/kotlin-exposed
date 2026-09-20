package com.atguigu.hr.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 密码哈希的迭代次数下限和格式校验属于安全边界，改动必须让这里失败。 */
class PasswordHasherTest {
    private val password = "correct horse battery staple"

    @Test
    fun `能校验自己生成的哈希并拒绝错误密码`() = runBlocking {
        val encoded = PasswordHasher.hash(password)
        assertTrue(encoded.startsWith("pbkdf2-sha256\$600000\$"))
        assertTrue(PasswordHasher.verify(password, encoded))
        assertFalse(PasswordHasher.verify("$password ", encoded))
        assertFalse(PasswordHasher.verify("", encoded))
    }

    @Test
    fun `同一密码每次哈希都不同`() = runBlocking {
        assertNotEquals(PasswordHasher.hash(password), PasswordHasher.hash(password))
    }

    @Test
    fun `拒绝格式错误或降低迭代次数的哈希`() = runBlocking {
        val encoded = PasswordHasher.hash(password)
        val parts = encoded.split('$')
        val salt = parts[2]
        val digest = parts[3]
        assertFalse(PasswordHasher.verify(password, ""))
        assertFalse(PasswordHasher.verify(password, "plain-text"))
        assertFalse(PasswordHasher.verify(password, "md5\$600000\$$salt\$$digest"))
        // 迭代次数低于下限时直接判失败，避免用弱哈希绕过
        assertFalse(PasswordHasher.verify(password, "pbkdf2-sha256\$1000\$$salt\$$digest"))
        assertFalse(PasswordHasher.verify(password, "pbkdf2-sha256\$600000\$$salt"))
        assertFalse(PasswordHasher.verify(password, "pbkdf2-sha256\$600000\$not-base64\$$digest"))
    }
}
