package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList

/** 认证持久层契约；角色权限替换及关联账号 Token 撤销必须在同一事务中完成。 */

interface AuthStore {
    /** 查询用户名对应的账号。 */
    suspend fun findByUsername(username: String): AuthUser?
    /** 查询主键对应的账号。 */
    suspend fun findById(id: Int): AuthUser?
    /** 查询用户列表。 */
    suspend fun listUsers(limit: Int, offset: Long): ApiList<UserDto>
    /** 查询角色列表。 */
    suspend fun listRoles(): List<RoleDto>
    /** 新增角色。 */
    suspend fun createRole(code: String, name: String): RoleDto
    /** 更新角色。 */
    suspend fun updateRole(code: String, body: RoleUpdateRequest): RoleDto?
    /** 查询角色权限。 */
    suspend fun getRolePermissions(code: String): RolePermissionsDto?
    /** 新增用户。 */
    suspend fun createUser(username: String, passwordHash: String, roleCode: String): AuthUser
    /** 更新用户。 */
    suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?): AuthUser?
    /** 删除账号；返回是否真的删掉了记录。 */
    suspend fun deleteUser(id: Int): Boolean
    /** 删除角色及其权限配置；仍有账号引用时抛冲突。 */
    suspend fun deleteRole(code: String): Boolean
    /** 提升用户 Token 版本，使该用户已有 Token 失效。 */
    suspend fun revokeTokens(id: Int)
    /** 整体替换角色权限，并处理关联的鉴权缓存失效。 */
    suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto
}
