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

    /** 记录一次登录失败，并按策略更新限流状态。 */
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

    /** 根据账号和 IP 的失败状态计算限流拒绝结果。 */
    fun rejection(): AuthException =
        AuthException(HttpStatusCode.TooManyRequests, "too many login attempts", ErrorCode.RATE_LIMITED)

    /** 生成规范化用户名对应的登录限流键。 */
    private fun accountKey(username: String) = username.trim().lowercase()

    companion object {
        /** 创建指定窗口和阈值的登录限流器。 */
        fun of(enabled: Boolean, windowSeconds: Long, maxAccountFailures: Int, maxAddressFailures: Int) =
            LoginThrottle(
                enabled = enabled,
                addresses = LoginRateLimiter(maxAddressFailures, windowSeconds * 1000),
                accounts = LoginRateLimiter(maxAccountFailures, windowSeconds * 1000),
            )
    }
}
