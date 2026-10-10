package com.atguigu.hrspring.auth

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 密码哈希,存储格式 pbkdf2-sha256$<迭代数>$<盐B64>$<哈希B64>,
 * 与 Ktor 后端生成的哈希互相兼容。校验接受 600000..1200000 迭代,便于将来提价。
 */
@Component
class PasswordHasher {

    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val hash = pbkdf2(password, salt, ITERATIONS)
        return "pbkdf2-sha256$$ITERATIONS$${b64(salt)}$${b64(hash)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in MIN_VERIFY_ITERATIONS..MAX_VERIFY_ITERATIONS) return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        if (salt.size != 16 || expected.size != 32) return false
        val actual = pbkdf2(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /** 用户不存在时用随机哈希做等额计算,抹平响应时序差异。 */
    fun dummyHash(): String = hash(random.nextInt().toString())

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    companion object {
        const val ITERATIONS = 600_000
        const val MIN_VERIFY_ITERATIONS = 600_000
        const val MAX_VERIFY_ITERATIONS = 1_200_000
    }
}
