package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ErrorCode
import com.atguigu.hr.common.database.isConstraintConflict
import io.ktor.http.HttpStatusCode
import java.util.Locale

/** 登录、账号与角色管理的业务校验入口；权限仅从角色获得，不接受账号级覆盖。 */

class AuthService(
    private val store: AuthStore,
    private val tokens: TokenService,
    private val dummyHash: String,
    /** 每个受保护请求都要解析一次用户与权限，用短 TTL 缓存吸收并发重复查询。 */
    private val userCache: AuthUserCache = AuthUserCache(),
) {
    suspend fun login(body: LoginRequest): TokenDto {
        val username = body.username.trim().lowercase(Locale.ROOT)
        if (!validUsername(username) || body.password.length !in 1..128) unauthorized()
        val user = store.findByUsername(username)
        val verified = PasswordHasher.verify(body.password, user?.passwordHash ?: dummyHash)
        if (!verified || user == null || !user.active) unauthorized()
        return tokens.issue(user)
    }

    suspend fun authenticate(id: Int, version: Int): AuthUser? {
        userCache.get(id, version)?.let { return it }
        // 只缓存通过校验的活跃用户；停用或版本不符时保持 401
        val loadedAt = userCache.now()
        val user = store.findById(id)?.takeIf { it.active && it.tokenVersion == version } ?: return null
        userCache.put(user, loadedAt)
        return user
    }

    suspend fun listUsers(limit: Int, offset: Long) = store.listUsers(limit, offset)
    suspend fun listRoles() = store.listRoles()

    suspend fun getRolePermissions(actor: AuthUser, code: String): RolePermissionsDto {
        requirePermissionAdmin(actor, "GET")
        return store.getRolePermissions(code) ?: throw AuthException(HttpStatusCode.NotFound, "role not found")
    }

    suspend fun updateRolePermissions(actor: AuthUser, code: String, body: RolePermissionsRequest): RolePermissionsDto {
        requirePermissionAdmin(actor, "PUT")
        if (body.revision < 0) badRequest("invalid permissions revision")
        return store.replaceRolePermissions(code, body).also { userCache.invalidateRole(code) }
    }

    private fun requirePermissionAdmin(actor: AuthUser, method: String) {
        if (actor.roleCode != RoleCode.ADMIN.name || !actor.hasPermission("api:$method:/api/auth/roles/{code}/permissions")) {
            throw AuthException(HttpStatusCode.Forbidden, "only ADMIN can manage permissions", ErrorCode.ADMIN_ONLY)
        }
    }

    suspend fun createRole(body: RoleCreateRequest): RoleDto {
        val code = body.code.trim().uppercase(Locale.ROOT)
        if (!Regex("[A-Z][A-Z0-9_]{1,19}").matches(code)) badRequest("role code must contain 2-20 uppercase letters, digits or underscores and start with a letter")
        val name = validRoleName(body.name)
        return try {
            store.createRole(code, name)
        } catch (cause: Exception) {
            if (cause.isConstraintConflict()) throw AuthException(HttpStatusCode.Conflict, "role already exists")
            throw cause
        }
    }

    suspend fun updateRole(code: String, body: RoleUpdateRequest): RoleDto {
        if (body.name == null && body.enabled == null) badRequest("no fields to update")
        return store.updateRole(code, body.copy(name = body.name?.let(::validRoleName)))?.also {
            userCache.invalidateRole(code)
        } ?: throw AuthException(HttpStatusCode.NotFound, "role not found")
    }

    private fun validRoleName(name: String): String = name.trim().also {
        if (it.isBlank() || it.length > 50) badRequest("role name must contain 1-50 characters")
    }

    suspend fun createUser(body: UserCreateRequest): UserDto {
        val username = normalizeUsername(body.username)
        validatePassword(body.password)
        validateRole(body.roleCode)
        val hash = PasswordHasher.hash(body.password)
        return try {
            store.createUser(username, hash, body.roleCode).toDto()
        } catch (cause: Exception) {
            if (cause.isConstraintConflict()) {
                throw AuthException(
                    HttpStatusCode.Conflict,
                    "username already exists or role is unavailable",
                    ErrorCode.USERNAME_TAKEN,
                )
            }
            throw cause
        }
    }

    suspend fun updateUser(actorId: Int, id: Int, body: UserUpdateRequest): UserDto {
        if (body.roleCode == null && body.enabled == null && body.password == null) badRequest("no fields to update")
        if (actorId == id && (body.enabled == false || (body.roleCode != null && body.roleCode != RoleCode.ADMIN.name))) {
            throw AuthException(
                HttpStatusCode.BadRequest,
                "cannot disable or demote your own administrator account",
                ErrorCode.SELF_DEMOTION,
            )
        }
        val target = store.findById(id) ?: throw AuthException(HttpStatusCode.NotFound, "user not found")
        if (target.protectedAccount && (body.enabled == false ||
                (body.roleCode != null && body.roleCode != RoleCode.ADMIN.name) ||
                (body.password != null && actorId != id))) {
            throw AuthException(
                HttpStatusCode.Forbidden,
                "admin account is protected",
                ErrorCode.ADMIN_ACCOUNT_PROTECTED,
            )
        }
        body.roleCode?.let { validateRole(it) }
        body.password?.let { validatePassword(it) }
        val hash = body.password?.let { PasswordHasher.hash(it) }
        return store.updateUser(id, body.roleCode, body.enabled, hash)?.toDto()?.also {
            userCache.invalidateUser(id)
        } ?: throw AuthException(HttpStatusCode.NotFound, "user not found")
    }

    /**
     * 删除账号。内置 admin 账号与当前登录账号都不能删除：
     * 前者是管理入口，后者会把自己锁在门外。
     */
    suspend fun deleteUser(actorId: Int, id: Int): Boolean {
        val target = store.findById(id) ?: throw AuthException(HttpStatusCode.NotFound, "user not found")
        if (target.protectedAccount) {
            throw AuthException(
                HttpStatusCode.Forbidden,
                "admin account is protected",
                ErrorCode.ADMIN_ACCOUNT_PROTECTED,
            )
        }
        if (id == actorId) {
            throw AuthException(
                HttpStatusCode.BadRequest,
                "cannot delete your own account",
                ErrorCode.SELF_DELETION,
            )
        }
        return store.deleteUser(id).also { deleted ->
            if (deleted) userCache.invalidateUser(id)
        }
    }

    /** 删除角色。ADMIN 角色受保护，仓库层还会拦下仍有账号引用的角色。 */
    suspend fun deleteRole(code: String): Boolean {
        if (code == RoleCode.ADMIN.name) {
            throw AuthException(HttpStatusCode.Forbidden, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
        }
        return store.deleteRole(code)
    }

    /** 退出登录：先吊销令牌（版本 +1），再清掉鉴权缓存里该用户的所有条目。 */
    suspend fun logout(id: Int) {
        store.revokeTokens(id)
        userCache.invalidateUser(id)
    }

    private suspend fun validateRole(code: String) {
        if (store.listRoles().none { it.code == code && it.enabled }) {
            badRequest("role must exist and be enabled")
        }
    }

    companion object {
        fun normalizeUsername(value: String): String {
            val username = value.trim().lowercase(Locale.ROOT)
            if (!validUsername(username)) badRequest("username must contain 3-64 ASCII letters, digits, dots, underscores or hyphens")
            return username
        }

        fun validatePassword(password: String) {
            if (password.length !in 8..128 || password.isBlank()) badRequest("password must contain 8-128 characters and must not be blank")
        }

        private fun validUsername(value: String) = Regex("[a-z0-9._-]{3,64}").matches(value)
        private fun badRequest(message: String): Nothing = throw AuthException(HttpStatusCode.BadRequest, message)
        /** 登录失败统一用 invalid_credentials，前端据此提示"账号或密码错误"而不是"会话过期"。 */
        private fun unauthorized(): Nothing = throw AuthException(
            HttpStatusCode.Unauthorized,
            "invalid username or password",
            ErrorCode.INVALID_CREDENTIALS,
        )
    }
}
