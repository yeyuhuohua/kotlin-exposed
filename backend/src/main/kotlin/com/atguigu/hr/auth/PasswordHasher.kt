package com.atguigu.hr.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 使用随机盐的 PBKDF2-HMAC-SHA256 保存密码；计算移到工作线程，比较使用恒定时间方法。 */

object PasswordHasher {
    private const val ITERATIONS = 600_000
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    suspend fun hash(password: String): String = withContext(Dispatchers.Default) {
        val salt = ByteArray(16).also(random::nextBytes)
        "pbkdf2-sha256\$$ITERATIONS\$${encoder.encodeToString(salt)}\$${encoder.encodeToString(derive(password, salt, ITERATIONS))}"
    }

    suspend fun verify(password: String, encoded: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val parts = encoded.split('$')
            if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return@withContext false
            val iterations = parts[1].toIntOrNull() ?: return@withContext false
            if (iterations !in ITERATIONS..1_200_000) return@withContext false
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            if (salt.size != 16 || expected.size != 32) return@withContext false
            MessageDigest.isEqual(expected, derive(password, salt, iterations))
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
