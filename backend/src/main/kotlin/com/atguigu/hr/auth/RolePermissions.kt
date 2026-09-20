package com.atguigu.hr.auth

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** 角色权限版本与明细映射；普通角色的空授权集合表示拒绝全部可配置访问。 */

object RolePermissionProfiles : Table("auth_role_permission_profiles") {
    val roleCode = varchar("role_code", 20).references(Roles.code, onDelete = ReferenceOption.CASCADE)
    val revision = integer("revision").default(0)
    override val primaryKey = PrimaryKey(roleCode)
}

object RolePermissions : Table("auth_role_permissions") {
    val roleCode = varchar("role_code", 20).references(RolePermissionProfiles.roleCode, onDelete = ReferenceOption.CASCADE)
    val permissionCode = varchar("permission_code", 160)
    override val primaryKey = PrimaryKey(roleCode, permissionCode)
}
