package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.validation.Password
import com.atguigu.hrspring.common.validation.RoleCode
import com.atguigu.hrspring.common.validation.RoleName
import com.atguigu.hrspring.common.validation.Username
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min

/** 登录体。用户名在服务里 trim + lowercase，这里不改写。 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginRequest(
    val username: String,
    val password: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserCreateRequest(
    @field:Username
    val username: String,
    @field:Password
    val password: String,
    val roleCode: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserUpdateRequest(
    val roleCode: String? = null,
    val enabled: Boolean? = null,
    @field:Password
    val password: String? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean = roleCode != null || enabled != null || password != null
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoleCreateRequest(
    @field:RoleCode(message = "role code must contain 2-20 uppercase letters, digits or underscores and start with a letter")
    val code: String,
    @field:RoleName
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RoleUpdateRequest(
    @field:RoleName
    val name: String? = null,
    val enabled: Boolean? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean = name != null || enabled != null
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RolePermissionsRequest(
    @field:Min(value = 0, message = "invalid permissions revision")
    val revision: Int,
    val permissions: Set<String>,
)
