package com.atguigu.hrspring.service

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.AuthUserCache
import com.atguigu.hrspring.auth.PermissionCatalog
import com.atguigu.hrspring.auth.RoleDto
import com.atguigu.hrspring.auth.RolePermissionsDto
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.common.validation.RoleCode
import com.atguigu.hrspring.dto.RoleCreateRequest
import com.atguigu.hrspring.dto.RolePermissionsRequest
import com.atguigu.hrspring.dto.RoleUpdateRequest
import com.atguigu.hrspring.entity.RoleEntity
import com.atguigu.hrspring.entity.RolePermissionEntity
import com.atguigu.hrspring.entity.RolePermissionProfileEntity
import com.atguigu.hrspring.entity.UserEntity
import com.atguigu.hrspring.mapper.RoleMapper
import com.atguigu.hrspring.mapper.RolePermissionMapper
import com.atguigu.hrspring.mapper.RolePermissionProfileMapper
import com.atguigu.hrspring.mapper.UserMapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtUpdateWrapper
import jakarta.validation.Valid
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.validation.annotation.Validated
import java.util.Locale

/** 角色 CRUD、权限版本管理及角色级令牌撤销，与登录和账号管理分开。 */
@Validated
internal class RolePermissionService(
    transactionManager: PlatformTransactionManager,
    private val userMapper: UserMapper,
    private val roleMapper: RoleMapper,
    private val rolePermissionMapper: RolePermissionMapper,
    private val profileMapper: RolePermissionProfileMapper,
    private val userCache: AuthUserCache,
    private val menuService: MenuService,
) {
    private val tx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun listRoles(): List<RoleDto> =
        roleMapper.selectList(KtQueryWrapper(RoleEntity::class.java).orderByAsc(RoleEntity::code))
            .map { it.toDto() }

    fun createRole(@Valid body: RoleCreateRequest): RoleDto {
        val code = body.code.trim().uppercase(Locale.ROOT)
        val name = body.name.trim()
        val created = try {
            tx.execute<RoleDto> {
                roleMapper.insert(
                    RoleEntity().apply {
                        this.code = code
                        this.name = name
                        enabled = true
                    },
                )
                profileMapper.insert(
                    RolePermissionProfileEntity().apply {
                        roleCode = code
                        revision = 0
                    },
                )
                RoleDto(code, name, true)
            }
        } catch (ex: RuntimeException) {
            if (ex.isConstraintConflict()) throw ApiException(409, "role already exists")
            throw ex
        } ?: throw ApiException(409, "role already exists")
        return created
    }

    fun updateRole(@RoleCode rawCode: String, @Valid body: RoleUpdateRequest): RoleDto {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        val name = body.name?.trim()
        try {
            return tx.execute<RoleDto?> {
                if (code == Roles.ADMIN) {
                    throw ApiException(403, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
                }
                val row = roleMapper.selectOne(
                    KtQueryWrapper(RoleEntity::class.java).eq(RoleEntity::code, code).last("FOR UPDATE"),
                )
                if (row == null) {
                    null
                } else {
                    val enabledChanged = body.enabled != null && body.enabled != row.enabled
                    roleMapper.update(
                        null,
                        KtUpdateWrapper(RoleEntity::class.java)
                            .eq(RoleEntity::code, code)
                            .set(name != null, RoleEntity::name, name)
                            .set(body.enabled != null, RoleEntity::enabled, body.enabled),
                    )
                    if (enabledChanged) revokeRoleTokens(code)
                    RoleDto(code, name ?: row.name, body.enabled ?: row.enabled)
                }
            } ?: throw ApiException.notFound("role not found")
        } finally {
            userCache.invalidateAll()
        }
    }

    fun deleteRole(@RoleCode rawCode: String): Boolean {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        if (code == Roles.ADMIN) {
            throw ApiException(403, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
        }
        val removed = tx.execute<Boolean> {
            val row = roleMapper.selectOne(
                KtQueryWrapper(RoleEntity::class.java).eq(RoleEntity::code, code).last("FOR UPDATE"),
            )
            if (row == null) {
                false
            } else {
                val members = userMapper.selectCount(
                    KtQueryWrapper(UserEntity::class.java).eq(UserEntity::roleCode, code),
                )
                if (members > 0) {
                    throw ApiException(409, "role still has $members user(s)", ErrorCode.ROLE_IN_USE)
                }
                rolePermissionMapper.delete(
                    KtQueryWrapper(RolePermissionEntity::class.java).eq(RolePermissionEntity::roleCode, code),
                )
                profileMapper.delete(
                    KtQueryWrapper(RolePermissionProfileEntity::class.java).eq(RolePermissionProfileEntity::roleCode, code),
                )
                roleMapper.delete(KtQueryWrapper(RoleEntity::class.java).eq(RoleEntity::code, code)) > 0
            }
        } ?: false
        if (removed) {
            userCache.invalidateAll()
        }
        return removed
    }

    fun getRolePermissions(actor: AuthUser, @RoleCode rawCode: String): RolePermissionsDto {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        requirePermissionAdmin(actor, "GET")
        return readTx.execute<RolePermissionsDto?> { loadRolePermissions(code) }
            ?: throw ApiException.notFound("role not found")
    }

    fun updateRolePermissions(
        actor: AuthUser,
        @RoleCode rawCode: String,
        @Valid body: RolePermissionsRequest,
    ): RolePermissionsDto {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        requirePermissionAdmin(actor, "PUT")
        try {
            return tx.execute<RolePermissionsDto> { replaceRolePermissions(code, body) }
                ?: throw ApiException.notFound("role not found")
        } finally {
            userCache.invalidateAll()
        }
    }

    private fun loadRolePermissions(code: String): RolePermissionsDto? {
        val role = roleMapper.selectById(code) ?: return null
        val profile = profileMapper.selectOne(
            KtQueryWrapper(RolePermissionProfileEntity::class.java).eq(RolePermissionProfileEntity::roleCode, code),
        )
        return RolePermissionsDto(
            role = role.toDto(),
            revision = profile?.revision ?: 0,
            permissions = roleGrants(code),
            protectedRole = code == Roles.ADMIN,
        )
    }

    private fun roleGrants(code: String): Set<String> {
        if (code == Roles.ADMIN) return PermissionCatalog.defaults(code)
        return rolePermissionMapper.selectList(
            KtQueryWrapper(RolePermissionEntity::class.java).eq(RolePermissionEntity::roleCode, code),
        )
            .map { it.permissionCode }
            .filterTo(linkedSetOf()) { grantable(it) }
    }

    private fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto {
        if (code == Roles.ADMIN) {
            throw ApiException(403, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
        }
        roleMapper.selectOne(KtQueryWrapper(RoleEntity::class.java).eq(RoleEntity::code, code).last("FOR UPDATE"))
            ?: throw ApiException.notFound("role not found")
        val profile = profileMapper.selectOne(
            KtQueryWrapper(RolePermissionProfileEntity::class.java)
                .eq(RolePermissionProfileEntity::roleCode, code)
                .last("FOR UPDATE"),
        )
        val revision = profile?.revision ?: 0
        if (request.revision != revision) {
            throw ApiException(
                409,
                "permissions changed; reload before saving",
                ErrorCode.REVISION_CONFLICT,
            )
        }
        val menuAdminOnly = menuAdminOnlyByKey()
        if (request.permissions.any { grantError(it, menuAdminOnly) == ErrorCode.UNKNOWN_PERMISSION }) {
            throw ApiException(400, "unknown permission code", ErrorCode.UNKNOWN_PERMISSION)
        }
        if (request.permissions.any { grantError(it, menuAdminOnly) == ErrorCode.ADMIN_ONLY }) {
            throw ApiException(400, "management permissions require ADMIN role", ErrorCode.ADMIN_ONLY)
        }
        if (profile == null) {
            profileMapper.insert(
                RolePermissionProfileEntity().apply {
                    roleCode = code
                    this.revision = 0
                },
            )
        }
        profileMapper.update(
            null,
            KtUpdateWrapper(RolePermissionProfileEntity::class.java)
                .eq(RolePermissionProfileEntity::roleCode, code)
                .set(RolePermissionProfileEntity::revision, revision + 1),
        )
        rolePermissionMapper.delete(
            KtQueryWrapper(RolePermissionEntity::class.java).eq(RolePermissionEntity::roleCode, code),
        )
        for (permission in request.permissions) {
            rolePermissionMapper.insert(
                RolePermissionEntity().apply {
                    roleCode = code
                    permissionCode = permission
                },
            )
        }
        revokeRoleTokens(code)
        return loadRolePermissions(code) ?: throw ApiException.notFound("role not found")
    }

    private fun menuAdminOnlyByKey(): Map<String, Boolean> =
        menuService.pageDefinitions().associate { dto ->
            val raw = dto.code
            val key = if (raw.startsWith("page:")) raw.removePrefix("page:") else raw
            key to dto.adminOnly
        }

    private fun revokeRoleTokens(code: String) {
        userMapper.update(
            null,
            KtUpdateWrapper(UserEntity::class.java)
                .eq(UserEntity::roleCode, code)
                .setSql("token_version = token_version + 1"),
        )
    }

    private fun requirePermissionAdmin(actor: AuthUser, method: String) {
        val code = "api:$method:/api/auth/roles/{code}/permissions"
        if (actor.roleCode != Roles.ADMIN || !actor.hasPermission(code)) {
            throw ApiException(403, "only ADMIN can manage permissions", ErrorCode.ADMIN_ONLY)
        }
    }
}
