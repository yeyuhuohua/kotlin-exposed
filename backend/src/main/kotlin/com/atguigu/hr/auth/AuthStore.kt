package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList

/** 认证持久层契约；角色权限替换及关联账号 Token 撤销必须在同一事务中完成。 */

interface AuthStore {
    suspend fun findByUsername(username: String): AuthUser?
    suspend fun findById(id: Int): AuthUser?
    suspend fun listUsers(limit: Int, offset: Long): ApiList<UserDto>
    suspend fun listRoles(): List<RoleDto>
    suspend fun createRole(code: String, name: String): RoleDto
    suspend fun updateRole(code: String, body: RoleUpdateRequest): RoleDto?
    suspend fun getRolePermissions(code: String): RolePermissionsDto?
    suspend fun createUser(username: String, passwordHash: String, roleCode: String): AuthUser
    suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?): AuthUser?
    suspend fun revokeTokens(id: Int)
    suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto
}
