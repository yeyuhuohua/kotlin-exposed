package com.atguigu.hrspring.dto
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/** 登录体。用户名在服务里 trim + lowercase，这里不改写。 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginRequest(
    val username: String,
    val password: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserCreateRequest(
    val username: String,
    val password: String,
    val roleCode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserUpdateRequest(
    val roleCode: String? = null,
    val enabled: Boolean? = null,
    val password: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoleCreateRequest(
    val code: String,
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoleUpdateRequest(
    val name: String? = null,
    val enabled: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RolePermissionsRequest(
    val revision: Int,
    val permissions: Set<String>,
)
