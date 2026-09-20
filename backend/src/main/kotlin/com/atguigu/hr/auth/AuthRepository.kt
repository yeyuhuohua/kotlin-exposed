package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ApiList
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

class AuthRepository(private val database: R2dbcDatabase) : AuthStore {
    suspend fun initialize() = suspendTransaction(db = database) {
        SchemaUtils.create(Roles, Users, RolePermissionProfiles, RolePermissions)
        RoleCode.entries.forEach { role ->
            Roles.insertIgnore {
                it[code] = role.name
                it[name] = if (role == RoleCode.ADMIN) "Administrator" else "Read only"
            }
        }
        val tables = SchemaUtils.listTables().map { it.substringAfterLast('.').trim('`', '"').lowercase() }.toSet()
        for (role in Roles.selectAll().forUpdate().toList()) {
            val code = role[Roles.code]
            if (RolePermissionProfiles.selectAll().where { RolePermissionProfiles.roleCode eq code }.firstOrNull() != null) continue
            val grants = initialRolePermissions(code, tables)
            RolePermissionProfiles.insert { it[roleCode] = code }
            insertRoleGrants(code, grants)
            revokeRoleTokens(code)
        }
    }

    // Never overwrite an existing password or promote an existing account on restart.
    suspend fun bootstrapAdmin(username: String, passwordHash: String) = suspendTransaction(db = database) {
        Users.insertIgnore {
            it[Users.username] = username
            it[Users.passwordHash] = passwordHash
            it[roleCode] = RoleCode.ADMIN.name
        }
    }

    override suspend fun findByUsername(username: String): AuthUser? = suspendTransaction(db = database, readOnly = true) {
        val row = (Users innerJoin Roles).selectAll().where { Users.username eq username }.firstOrNull()
        row?.toAuthUser()?.let { loadPermissions(it) }
    }

    override suspend fun findById(id: Int): AuthUser? = suspendTransaction(db = database, readOnly = true) {
        loadUser(id)
    }

    override suspend fun listUsers(limit: Int, offset: Long): ApiList<UserDto> =
        suspendTransaction(db = database, readOnly = true) {
            val total = Users.selectAll().count()
            val items = Users.selectAll().orderBy(Users.id to SortOrder.ASC).limit(limit).offset(offset)
                .map { UserDto(it[Users.id], it[Users.username], it[Users.roleCode], it[Users.enabled]) }.toList()
            ApiList(total, items)
        }

    override suspend fun listRoles(): List<RoleDto> = suspendTransaction(db = database, readOnly = true) {
        Roles.selectAll().orderBy(Roles.code to SortOrder.ASC)
            .map { RoleDto(it[Roles.code], it[Roles.name], it[Roles.enabled]) }.toList()
    }

    override suspend fun createRole(code: String, name: String): RoleDto = suspendTransaction(db = database) {
        Roles.insert { it[Roles.code] = code; it[Roles.name] = name }
        RolePermissionProfiles.insert { it[roleCode] = code }
        RoleDto(code, name, true)
    }

    override suspend fun updateRole(code: String, body: RoleUpdateRequest): RoleDto? = suspendTransaction(db = database) {
        if (code == RoleCode.ADMIN.name) throw AuthException(HttpStatusCode.Forbidden, "ADMIN role is protected")
        val row = Roles.selectAll().where { Roles.code eq code }.forUpdate().firstOrNull() ?: return@suspendTransaction null
        Roles.update({ Roles.code eq code }) {
            body.name?.let { value -> it[name] = value }
            body.enabled?.let { value -> it[enabled] = value }
        }
        if (body.enabled != null && body.enabled != row[Roles.enabled]) revokeRoleTokens(code)
        RoleDto(code, body.name ?: row[Roles.name], body.enabled ?: row[Roles.enabled])
    }

    override suspend fun getRolePermissions(code: String): RolePermissionsDto? = suspendTransaction(db = database, readOnly = true) {
        loadRolePermissions(code)
    }

    override suspend fun createUser(username: String, passwordHash: String, roleCode: String): AuthUser =
        suspendTransaction(db = database) {
            requireEnabledRole(roleCode)
            val id = Users.insert {
                it[Users.username] = username
                it[Users.passwordHash] = passwordHash
                it[Users.roleCode] = roleCode
            }[Users.id]
            checkNotNull(loadUser(id))
        }

    override suspend fun updateUser(id: Int, roleCode: String?, enabled: Boolean?, passwordHash: String?): AuthUser? =
        suspendTransaction(db = database) {
            roleCode?.let { requireEnabledRole(it) }
            val rows = Users.update({ Users.id eq id }) {
                roleCode?.let { value -> it[Users.roleCode] = value }
                enabled?.let { value -> it[Users.enabled] = value }
                passwordHash?.let { value -> it[Users.passwordHash] = value }
                it[tokenVersion] = Users.tokenVersion + 1
            }
            if (rows == 0) null else loadUser(id)
        }

    override suspend fun revokeTokens(id: Int) {
        suspendTransaction(db = database) {
            Users.update({ Users.id eq id }) { it[tokenVersion] = Users.tokenVersion + 1 }
        }
    }

    override suspend fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto = suspendTransaction(db = database) {
        if (code == RoleCode.ADMIN.name) throw AuthException(HttpStatusCode.Forbidden, "ADMIN role is protected")
        // Role changes and user assignments take the same role lock.
        Roles.selectAll().where { Roles.code eq code }.forUpdate().firstOrNull()
            ?: throw AuthException(HttpStatusCode.NotFound, "role not found")
        val profile = checkNotNull(loadRolePermissions(code))
        if (request.revision != profile.revision) {
            throw AuthException(HttpStatusCode.Conflict, "permissions changed; reload before saving")
        }
        if (request.permissions.any { PermissionCatalog.byCode[it] == null }) {
            throw AuthException(HttpStatusCode.BadRequest, "unknown permission code")
        }
        if (request.permissions.any { PermissionCatalog.byCode[it]?.adminOnly == true }) {
            throw AuthException(HttpStatusCode.BadRequest, "management permissions require ADMIN role")
        }
        RolePermissionProfiles.insertIgnore { it[roleCode] = code }
        RolePermissionProfiles.update({ RolePermissionProfiles.roleCode eq code }) {
            it[revision] = profile.revision + 1
        }
        RolePermissions.deleteWhere { roleCode eq code }
        insertRoleGrants(code, request.permissions)
        revokeRoleTokens(code)
        checkNotNull(loadRolePermissions(code))
    }

    private suspend fun loadUser(id: Int): AuthUser? {
        val row = (Users innerJoin Roles).selectAll().where { Users.id eq id }.firstOrNull()
        return row?.toAuthUser()?.let { loadPermissions(it) }
    }

    private suspend fun loadPermissions(user: AuthUser): AuthUser {
        return user.copy(rolePermissions = roleGrants(user.roleCode))
    }

    private suspend fun loadRolePermissions(code: String): RolePermissionsDto? {
        val role = Roles.selectAll().where { Roles.code eq code }.firstOrNull() ?: return null
        val profile = RolePermissionProfiles.selectAll().where { RolePermissionProfiles.roleCode eq code }.firstOrNull()
        return RolePermissionsDto(
            RoleDto(code, role[Roles.name], role[Roles.enabled]),
            profile?.get(RolePermissionProfiles.revision) ?: 0,
            roleGrants(code),
            code == RoleCode.ADMIN.name,
        )
    }

    private suspend fun roleGrants(code: String): Set<String> {
        if (code == RoleCode.ADMIN.name) return PermissionCatalog.defaults(code)
        return RolePermissions.selectAll().where { RolePermissions.roleCode eq code }
            .map { it[RolePermissions.permissionCode] }.toList()
            .filterTo(linkedSetOf()) { PermissionCatalog.byCode[it]?.adminOnly == false }
    }

    private suspend fun insertRoleGrants(code: String, permissions: Set<String>) {
        permissions.forEach { permission ->
            RolePermissions.insert { it[roleCode] = code; it[permissionCode] = permission }
        }
    }

    private suspend fun revokeRoleTokens(code: String) {
        Users.update({ Users.roleCode eq code }) { it[tokenVersion] = Users.tokenVersion + 1 }
    }

    private suspend fun requireEnabledRole(code: String) {
        val role = Roles.selectAll().where { Roles.code eq code }.forUpdate().firstOrNull()
        if (role == null || !role[Roles.enabled]) throw AuthException(HttpStatusCode.BadRequest, "role must exist and be enabled")
    }
}

private fun ResultRow.toAuthUser() = AuthUser(
    id = this[Users.id],
    username = this[Users.username],
    passwordHash = this[Users.passwordHash],
    roleCode = this[Users.roleCode],
    enabled = this[Users.enabled],
    roleEnabled = this[Roles.enabled],
    tokenVersion = this[Users.tokenVersion],
)
