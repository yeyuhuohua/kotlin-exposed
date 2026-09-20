package com.atguigu.hr.auth

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.selectAll

// Read legacy overrides only while creating a role's first permission profile.
// Existing tables are retained for review; request authorization never reads them.
private object LegacyUserProfiles : Table("auth_user_permission_profiles") {
    val userId = integer("user_id")
    val inheritRole = bool("inherit_role")
}

private object LegacyUserGrants : Table("auth_user_permissions") {
    val userId = integer("user_id")
    val permissionCode = varchar("permission_code", 160)
}

internal suspend fun initialRolePermissions(roleCode: String, tables: Set<String>): Set<String> {
    val defaults = PermissionCatalog.defaults(roleCode)
    if (roleCode == RoleCode.ADMIN.name || "auth_user_permission_profiles" !in tables) return defaults
    val members = Users.selectAll().where { Users.roleCode eq roleCode }.map { it[Users.id] }.toList()
    if (members.isEmpty()) return defaults
    val customMembers = LegacyUserProfiles.selectAll().toList()
        .filter { it[LegacyUserProfiles.userId] in members && !it[LegacyUserProfiles.inheritRole] }
        .map { it[LegacyUserProfiles.userId] }.toSet()
    if (customMembers.isEmpty()) return defaults
    val grants = if ("auth_user_permissions" in tables) LegacyUserGrants.selectAll().toList()
        .groupBy({ it[LegacyUserGrants.userId] }, { it[LegacyUserGrants.permissionCode] }) else emptyMap()
    // Intersection avoids granting any member privileges they did not previously have.
    return members.map { id ->
        if (id in customMembers) grants[id].orEmpty().filterTo(linkedSetOf()) { code ->
            PermissionCatalog.byCode[code]?.adminOnly == false
        } else defaults
    }.reduce { shared, next -> shared.intersect(next) }
}
