package com.atguigu.hrspring.auth

/**
 * 当前登录用户的对外契约,登录响应与 /api/auth/me 共用。
 */
data class CurrentUserDto(
    val id: Int,
    val username: String,
    val roleCode: String,
    val enabled: Boolean,
    val permissions: Set<String>,
)

data class TokenDto(
    val accessToken: String,
    val expiresIn: Long,
    val user: CurrentUserDto,
    val tokenType: String = "Bearer",
)

data class UserDto(
    val id: Int,
    val username: String,
    val roleCode: String,
    val enabled: Boolean,
)

data class RoleDto(
    val code: String,
    val name: String,
    val enabled: Boolean,
)

data class RolePermissionsDto(
    val role: RoleDto,
    val revision: Int,
    val permissions: Set<String>,
    val protectedRole: Boolean,
)

/** 请求线程上的当前用户,由鉴权过滤器写入。 */
object CurrentUserHolder {
    private val holder = ThreadLocal<AuthUser?>()

    fun set(user: AuthUser?) = holder.set(user)

    fun get(): AuthUser? = holder.get()

    fun require(): AuthUser = get() ?: throw com.atguigu.hrspring.common.api.ApiException.unauthorized()

    fun clear() = holder.remove()
}
