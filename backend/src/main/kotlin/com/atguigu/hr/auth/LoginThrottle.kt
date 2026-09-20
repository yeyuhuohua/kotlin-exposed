package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ErrorCode
import io.ktor.http.HttpStatusCode

/**
 * 登录失败限流：同时按来源地址和账号计数。
 * 账号维度挡住针对单个账号的爆破，地址维度挡住换用户名的撞库。
 */
class LoginThrottle(
    private val enabled: Boolean = true,
    private val addresses: LoginRateLimiter = LoginRateLimiter(maxFailures = 30, windowMillis = 300_000),
    private val accounts: LoginRateLimiter = LoginRateLimiter(maxFailures = 8, windowMillis = 300_000),
) {
    /** 返回需要等待的秒数；null 表示放行。 */
    fun blockedSeconds(remoteHost: String, username: String): Long? {
        if (!enabled) return null
        return addresses.retryAfterSeconds(remoteHost) ?: accounts.retryAfterSeconds(accountKey(username))
    }

    fun recordFailure(remoteHost: String, username: String) {
        if (!enabled) return
        addresses.recordFailure(remoteHost)
        accounts.recordFailure(accountKey(username))
    }

    /** 登录成功后只清账号维度，地址维度保留，避免用一个可用账号刷掉地址计数。 */
    fun recordSuccess(username: String) {
        if (!enabled) return
        accounts.reset(accountKey(username))
    }

    fun rejection(): AuthException =
        AuthException(HttpStatusCode.TooManyRequests, "too many login attempts", ErrorCode.RATE_LIMITED)

    private fun accountKey(username: String) = username.trim().lowercase()

    companion object {
        fun of(enabled: Boolean, windowSeconds: Long, maxAccountFailures: Int, maxAddressFailures: Int) =
            LoginThrottle(
                enabled = enabled,
                addresses = LoginRateLimiter(maxAddressFailures, windowSeconds * 1000),
                accounts = LoginRateLimiter(maxAccountFailures, windowSeconds * 1000),
            )
    }
}
