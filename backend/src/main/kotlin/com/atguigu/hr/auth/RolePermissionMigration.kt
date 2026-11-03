package com.atguigu.hr.auth

import com.atguigu.hr.menu.Menus
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll

// 仅在创建角色的首个权限档案时读取旧版覆盖配置。
// 保留旧表供核查；请求鉴权绝不读取这些表。
private object LegacyUserProfiles : Table("auth_user_permission_profiles") {
    val userId = integer("user_id")
    val inheritRole = bool("inherit_role")
}

private object LegacyUserGrants : Table("auth_user_permissions") {
    val userId = integer("user_id")
    val permissionCode = varchar("permission_code", 160)
}

internal suspend fun initialRolePermissions(roleCode: String, tables: Set<String>): Set<String> {
    // 页面授权来自菜单表（运行时数据）：ADMIN 全量，其他角色只给启用的非仅管理员菜单。
    val defaults = PermissionCatalog.defaults(roleCode) + menuGrants(roleCode)
    if (roleCode == RoleCode.ADMIN.name || "auth_user_permission_profiles" !in tables) return defaults
    val members = Users.selectAll().where { Users.roleCode eq roleCode }.map { it[Users.id] }.toList()
    if (members.isEmpty()) return defaults
    val customMembers = LegacyUserProfiles.selectAll().toList()
        .filter { it[LegacyUserProfiles.userId] in members && !it[LegacyUserProfiles.inheritRole] }
        .map { it[LegacyUserProfiles.userId] }.toSet()
    if (customMembers.isEmpty()) return defaults
    val grants = if ("auth_user_permissions" in tables) LegacyUserGrants.selectAll().toList()
        .groupBy({ it[LegacyUserGrants.userId] }, { it[LegacyUserGrants.permissionCode] }) else emptyMap()
    // 取交集可避免授予成员此前没有的权限。
    return members.map { id ->
        if (id in customMembers) grants[id].orEmpty().filterTo(linkedSetOf(), ::grantableCode) else defaults
    }.reduce { shared, next -> shared.intersect(next) }
}

private suspend fun menuGrants(roleCode: String): Set<String> =
    Menus.selectAll().where { Menus.enabled eq true }
        .map { it[Menus.key] to it[Menus.adminOnly] }.toList()
        .filter { !it.second || roleCode == RoleCode.ADMIN.name }
        .mapTo(linkedSetOf()) { "page:${it.first}" }
