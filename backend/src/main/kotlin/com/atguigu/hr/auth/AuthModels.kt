package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ErrorCode
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

enum class RoleCode { ADMIN, READER }

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class UserCreateRequest(val username: String, val password: String, val roleCode: String = "READER")

@Serializable
data class UserUpdateRequest(
    val roleCode: String? = null,
    val enabled: Boolean? = null,
    val password: String? = null,
)

@Serializable
data class RoleDto(val code: String, val name: String, val enabled: Boolean)

@Serializable
data class RoleCreateRequest(val code: String, val name: String)

@Serializable
data class RoleUpdateRequest(val name: String? = null, val enabled: Boolean? = null)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val roleCode: String,
    val enabled: Boolean,
    /** 内置或配置指定的管理员；仅用于界面禁用受保护操作。 */
    val protected: Boolean = false,
)

@Serializable
data class CurrentUserDto(
    val id: Int,
    val username: String,
    val roleCode: String,
    val enabled: Boolean,
    val permissions: Set<String>,
)

@Serializable
data class RolePermissionsDto(
    val role: RoleDto,
    val revision: Int,
    val permissions: Set<String>,
    val protectedRole: Boolean,
)

@Serializable
data class RolePermissionsRequest(val revision: Int, val permissions: Set<String>)

    /** 将持久层数据转换为当前登录用户传输对象。 */
    /** 检查账号及角色是否启用，并判断是否拥有指定权限。 */
    /** 过滤无效权限并计算当前用户的有效权限集合。 */
    /** 将账号与权限数据转换为对外传输对象。 */
@Serializable
data class TokenDto(
    val accessToken: String,
    val expiresIn: Long,
    val user: CurrentUserDto,
    val tokenType: String = "Bearer",
)

// 密码哈希和令牌版本绝不能序列化到 API 响应中。
data class AuthUser(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val roleCode: String,
    val enabled: Boolean,
    val roleEnabled: Boolean,
    val tokenVersion: Int,
    val rolePermissions: Set<String> = emptySet(),
) {
    val active: Boolean get() = enabled && roleEnabled
    val protectedAccount: Boolean get() = username.equals(AuthService.PROTECTED_USERNAME, ignoreCase = true)
    fun toDto(protected: Boolean = protectedAccount) = UserDto(id, username, roleCode, enabled, protected)
    fun effectivePermissions() = PermissionCatalog.effective(this)
    fun hasPermission(code: String) = active && code in effectivePermissions()
    fun toCurrentUser() = CurrentUserDto(id, username, roleCode, enabled, effectivePermissions())
}

/** error 为稳定错误码，未指定时按状态码归类，客户端据此选择提示文案。 */
class AuthException(
    val status: HttpStatusCode,
    override val message: String,
    val error: String = ErrorCode.forStatus(status),
) : RuntimeException(message)
