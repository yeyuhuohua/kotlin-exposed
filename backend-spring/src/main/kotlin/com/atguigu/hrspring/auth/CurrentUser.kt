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
    /** 内置或配置指定的管理员；仅用于界面禁用受保护操作。 */
    val protected: Boolean = false,
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

/** HTTP 请求或 MCP 工具回调当前线程上的用户；MCP 身份由切面从协议上下文恢复并在执行后清理。 */
object CurrentUserHolder {
    private val holder = ThreadLocal<AuthUser?>()

    /** 把认证用户绑定到当前请求或 MCP 回调线程。 */
    fun set(user: AuthUser?) = holder.set(user)

    /** 返回当前线程绑定的认证用户；未绑定时返回 null。 */
    fun get(): AuthUser? = holder.get()

    /** 取得当前认证用户；缺少身份时抛出 401。 */
    fun require(): AuthUser = get() ?: throw com.atguigu.hrspring.common.api.ApiException.unauthorized()

    /** 移除当前线程的用户，避免线程复用造成身份串联。 */
    fun clear() = holder.remove()
}
