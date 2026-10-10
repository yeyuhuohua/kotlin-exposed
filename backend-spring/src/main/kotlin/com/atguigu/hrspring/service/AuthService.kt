package com.atguigu.hrspring.service
import com.atguigu.hrspring.audit.AuditService
import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.AuthUserCache
import com.atguigu.hrspring.auth.AuthUserLoader
import com.atguigu.hrspring.auth.CurrentUserDto
import com.atguigu.hrspring.auth.LoginThrottle
import com.atguigu.hrspring.auth.PasswordHasher
import com.atguigu.hrspring.auth.PermissionCatalog
import com.atguigu.hrspring.auth.PermissionDefinition
import com.atguigu.hrspring.auth.RoleDto
import com.atguigu.hrspring.auth.RolePermissionsDto
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.auth.TokenDto
import com.atguigu.hrspring.auth.TokenService
import com.atguigu.hrspring.auth.UserDto
import com.atguigu.hrspring.dto.LoginRequest
import com.atguigu.hrspring.dto.RoleCreateRequest
import com.atguigu.hrspring.dto.RolePermissionsRequest
import com.atguigu.hrspring.dto.RoleUpdateRequest
import com.atguigu.hrspring.dto.UserCreateRequest
import com.atguigu.hrspring.dto.UserUpdateRequest
import com.atguigu.hrspring.entity.RoleEntity
import com.atguigu.hrspring.entity.RolePermissionEntity
import com.atguigu.hrspring.entity.RolePermissionProfileEntity
import com.atguigu.hrspring.entity.UserEntity
import com.atguigu.hrspring.mapper.RoleMapper
import com.atguigu.hrspring.mapper.RolePermissionMapper
import com.atguigu.hrspring.mapper.RolePermissionProfileMapper
import com.atguigu.hrspring.mapper.UserMapper
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.ClientIp
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.MenuPermissionDto
import com.atguigu.hrspring.dto.PageRoute
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.Locale

/**
 * 登录、账号与角色管理。权限只来自角色；页面码在返回当前用户时按启用菜单补齐。
 */
@Service
class AuthService(
    transactionManager: PlatformTransactionManager,
    private val userMapper: UserMapper,
    private val roleMapper: RoleMapper,
    private val rolePermissionMapper: RolePermissionMapper,
    private val profileMapper: RolePermissionProfileMapper,
    private val passwordHasher: PasswordHasher,
    private val tokenService: TokenService,
    private val userCache: AuthUserCache,
    private val loginThrottle: LoginThrottle,
    private val redisCache: RedisCache,
    private val menuService: MenuService,
    private val auditService: AuditService,
    private val clientIp: ClientIp,
) : AuthUserLoader {
    private val tx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /** 用户不存在时拿来做等长校验，避免用响应时间判断账号是否存在。 */
    private val dummyHash: String = passwordHasher.hash("hr-spring-dummy-password")

    fun login(body: LoginRequest): TokenDto {
        val request = currentRequest()
        val ip = request?.let { clientIp.resolve(it) } ?: "unknown"
        val userAgent = request?.getHeader("User-Agent")
        val auditUsername = body.username.trim().lowercase(Locale.ROOT).take(64)
        val accountKey = loginThrottle.accountKey(body.username.trim().lowercase(Locale.ROOT))
        val addressKey = loginThrottle.addressKey(ip)
        val retryAfter = loginThrottle.blockedSeconds(accountKey, addressKey)
        if (retryAfter != null) {
            auditService.recordLogin(auditUsername, null, ip, userAgent, false, ErrorCode.RATE_LIMITED)
            markRetryAfter(retryAfter)
            throw ApiException(429, "too many login attempts, retry later", ErrorCode.RATE_LIMITED)
        }
        try {
            val token = issueLogin(body)
            loginThrottle.recordSuccess(accountKey)
            auditService.recordLogin(auditUsername, token.user.id, ip, userAgent, true, null)
            return token
        } catch (ex: ApiException) {
            if (ex.status == 401) {
                loginThrottle.recordFailure(accountKey, addressKey)
                auditService.recordLogin(auditUsername, null, ip, userAgent, false, ErrorCode.INVALID_CREDENTIALS)
            }
            throw ex
        }
    }

    override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? {
        userCache.get(userId, tokenVersion)?.let { return it }
        val loadGeneration = userCache.generation()
        val user = readTx.execute<AuthUser?> {
            loadUser(userId)?.takeIf { it.active && it.tokenVersion == tokenVersion }
        }
        if (user != null) {
            userCache.put(userId, tokenVersion, user, loadGeneration)
        }
        return user
    }

    override fun loadActiveAdmin(): AuthUser? = readTx.execute<AuthUser?> {
        val row = userMapper.selectOne(QueryWrapper<UserEntity>().eq("username", "admin")) ?: return@execute null
        val role = roleMapper.selectById(row.roleCode) ?: return@execute null
        toAuthUser(row, role).takeIf { it.active && it.roleCode == Roles.ADMIN }
    }

    /**
     * 返回给前端的当前用户。ADMIN 补上全部启用菜单的页面码；
     * 其他角色只保留仍启用菜单的页面码。
     */
    fun currentUser(user: AuthUser): CurrentUserDto {
        val base = CurrentUserDto(
            id = user.id,
            username = user.username,
            roleCode = user.roleCode,
            enabled = user.enabled,
            permissions = PermissionCatalog.effective(user),
        )
        val menuCodes = menuService.enabledKeys().mapTo(linkedSetOf()) { "page:$it" }
        val permissions = if (user.roleCode == Roles.ADMIN) {
            base.permissions + menuCodes
        } else {
            base.permissions.filterTo(linkedSetOf()) { !it.startsWith("page:") || it in menuCodes }
        }
        return base.copy(permissions = permissions)
    }

    fun pageRoutes(user: AuthUser): List<PageRoute> = menuService.pagesFor(user)

    fun listPermissions(): List<PermissionDefinition> =
        menuService.pageDefinitions().map { it.toPermissionDefinition() } + PermissionCatalog.definitions

    fun logout(id: Int) {
        try {
            tx.execute<Int> {
                userMapper.update(
                    null,
                    UpdateWrapper<UserEntity>()
                        .eq("id", id)
                        .setSql("token_version = token_version + 1"),
                )
            }
        } finally {
            userCache.invalidateUser(id)
        }
    }

    fun listUsers(limit: Int?, offset: Long?): Pair<ApiList<UserDto>, Boolean> {
        val size = (limit ?: 50).coerceIn(1, 200)
        val skip = (offset ?: 0L).coerceAtLeast(0L)
        val key = CacheNames.USERS_PREFIX + "$size:$skip"
        return redisCache.withPage(key, UserDto::class.java) {
            val total = userMapper.selectCount(QueryWrapper<UserEntity>())
            val items = userMapper.selectList(
                QueryWrapper<UserEntity>()
                    .orderByAsc("id")
                    .last("LIMIT $size OFFSET $skip"),
            ).map { it.toDto() }
            ApiList(total, items)
        }
    }

    fun listRoles(): Pair<List<RoleDto>, Boolean> =
        redisCache.withCacheList(CacheNames.ROLES, RoleDto::class.java) {
            roleMapper.selectList(QueryWrapper<RoleEntity>().orderByAsc("code"))
                .map { it.toDto() }
        }

    fun createUser(body: UserCreateRequest): UserDto {
        val username = normalizeUsername(body.username)
        if (username == PROTECTED_USERNAME) {
            throw ApiException(409, "username is reserved", ErrorCode.USERNAME_TAKEN)
        }
        validatePassword(body.password)
        val roleCode = body.roleCode ?: Roles.READER
        validateRole(roleCode)
        val hash = passwordHasher.hash(body.password)
        val created = try {
            tx.execute<UserDto> {
                requireEnabledRole(roleCode)
                val entity = UserEntity().apply {
                    this.username = username
                    passwordHash = hash
                    this.roleCode = roleCode
                    enabled = true
                    tokenVersion = 0
                }
                userMapper.insert(entity)
                loadUser(entity.id)?.toUserDto() ?: throw ApiException.notFound("user not found")
            }
        } catch (ex: RuntimeException) {
            if (ex.isConstraintConflict()) {
                throw ApiException(
                    409,
                    "username already exists or role is unavailable",
                    ErrorCode.USERNAME_TAKEN,
                )
            }
            throw ex
        } ?: throw ApiException.notFound("user not found")
        invalidateUsers()
        return created
    }

    fun updateUser(actorId: Int, id: Int, body: UserUpdateRequest): UserDto {
        if (id <= 0) badRequest("invalid user id")
        if (body.roleCode == null && body.enabled == null && body.password == null) {
            badRequest("no fields to update")
        }
        if (actorId == id && (body.enabled == false || (body.roleCode != null && body.roleCode != Roles.ADMIN))) {
            throw ApiException(
                400,
                "cannot disable or demote your own administrator account",
                ErrorCode.SELF_DEMOTION,
            )
        }
        val target = readTx.execute<AuthUser?> { loadUser(id) } ?: throw ApiException.notFound("user not found")
        if (target.isProtectedAccount() && (body.enabled == false ||
                (body.roleCode != null && body.roleCode != Roles.ADMIN) ||
                (body.password != null && actorId != id))
        ) {
            throw ApiException(403, "admin account is protected", ErrorCode.ADMIN_ACCOUNT_PROTECTED)
        }
        body.roleCode?.let { validateRole(it) }
        body.password?.let { validatePassword(it) }
        val hash = body.password?.let { passwordHasher.hash(it) }
        try {
            return tx.execute<UserDto?> {
                body.roleCode?.let { requireEnabledRole(it) }
                val rows = userMapper.update(
                    null,
                    UpdateWrapper<UserEntity>()
                        .eq("id", id)
                        .set(body.roleCode != null, "role_code", body.roleCode)
                        .set(body.enabled != null, "enabled", body.enabled)
                        .set(hash != null, "password_hash", hash)
                        .setSql("token_version = token_version + 1"),
                )
                if (rows == 0) null else loadUser(id)?.toUserDto()
            } ?: throw ApiException.notFound("user not found")
        } finally {
            userCache.invalidateUser(id)
            invalidateUsers()
        }
    }

    fun deleteUser(actorId: Int, id: Int): Boolean {
        if (id <= 0) badRequest("invalid user id")
        val target = readTx.execute<AuthUser?> { loadUser(id) } ?: throw ApiException.notFound("user not found")
        if (target.isProtectedAccount()) {
            throw ApiException(403, "admin account is protected", ErrorCode.ADMIN_ACCOUNT_PROTECTED)
        }
        if (id == actorId) {
            throw ApiException(400, "cannot delete your own account", ErrorCode.SELF_DELETION)
        }
        return try {
            tx.execute<Boolean> {
                userMapper.deleteById(id) > 0
            } ?: false
        } finally {
            userCache.invalidateUser(id)
            invalidateUsers()
        }
    }

    fun createRole(body: RoleCreateRequest): RoleDto {
        val code = body.code.trim().uppercase(Locale.ROOT)
        if (!ROLE_CODE_PATTERN.matches(code)) {
            badRequest("role code must contain 2-20 uppercase letters, digits or underscores and start with a letter")
        }
        val name = validRoleName(body.name)
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
        invalidateRoles()
        return created
    }

    fun updateRole(rawCode: String, body: RoleUpdateRequest): RoleDto {
        val code = requireRoleCode(rawCode)
        if (body.name == null && body.enabled == null) badRequest("no fields to update")
        val name = body.name?.let { validRoleName(it) }
        try {
            return tx.execute<RoleDto?> {
                if (code == Roles.ADMIN) {
                    throw ApiException(403, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
                }
                val row = roleMapper.selectOne(QueryWrapper<RoleEntity>().eq("code", code).last("FOR UPDATE"))
                if (row == null) {
                    null
                } else {
                    val enabledChanged = body.enabled != null && body.enabled != row.enabled
                    roleMapper.update(
                        null,
                        UpdateWrapper<RoleEntity>()
                            .eq("code", code)
                            .set(name != null, "name", name)
                            .set(body.enabled != null, "enabled", body.enabled),
                    )
                    if (enabledChanged) revokeRoleTokens(code)
                    RoleDto(code, name ?: row.name, body.enabled ?: row.enabled)
                }
            } ?: throw ApiException.notFound("role not found")
        } finally {
            userCache.invalidateAll()
            invalidateRoles()
        }
    }

    fun deleteRole(rawCode: String): Boolean {
        val code = requireRoleCode(rawCode)
        if (code == Roles.ADMIN) {
            throw ApiException(403, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
        }
        val removed = tx.execute<Boolean> {
            val row = roleMapper.selectOne(QueryWrapper<RoleEntity>().eq("code", code).last("FOR UPDATE"))
            if (row == null) {
                false
            } else {
                val members = userMapper.selectCount(QueryWrapper<UserEntity>().eq("role_code", code))
                if (members > 0) {
                    throw ApiException(409, "role still has $members user(s)", ErrorCode.ROLE_IN_USE)
                }
                rolePermissionMapper.delete(QueryWrapper<RolePermissionEntity>().eq("role_code", code))
                profileMapper.delete(QueryWrapper<RolePermissionProfileEntity>().eq("role_code", code))
                roleMapper.delete(QueryWrapper<RoleEntity>().eq("code", code)) > 0
            }
        } ?: false
        if (removed) {
            invalidateRoles()
            userCache.invalidateAll()
        }
        return removed
    }

    fun getRolePermissions(actor: AuthUser, rawCode: String): RolePermissionsDto {
        val code = requireRoleCode(rawCode)
        requirePermissionAdmin(actor, "GET")
        return readTx.execute<RolePermissionsDto?> { loadRolePermissions(code) }
            ?: throw ApiException.notFound("role not found")
    }

    fun updateRolePermissions(actor: AuthUser, rawCode: String, body: RolePermissionsRequest): RolePermissionsDto {
        val code = requireRoleCode(rawCode)
        requirePermissionAdmin(actor, "PUT")
        if (body.revision < 0) badRequest("invalid permissions revision")
        try {
            return tx.execute<RolePermissionsDto> { replaceRolePermissions(code, body) }
                ?: throw ApiException.notFound("role not found")
        } finally {
            userCache.invalidateAll()
        }
    }

    private fun issueLogin(body: LoginRequest): TokenDto {
        val username = body.username.trim().lowercase(Locale.ROOT)
        if (!USERNAME_PATTERN.matches(username) || body.password.length !in 1..128) unauthorized()
        val loaded = readTx.execute<Pair<AuthUser, String>?> { findForLogin(username) }
        val verified = passwordHasher.verify(body.password, loaded?.second ?: dummyHash)
        val user = loaded?.first
        if (!verified || user == null || !user.active) unauthorized()
        return TokenDto(
            accessToken = tokenService.issue(user.id, user.tokenVersion),
            expiresIn = tokenService.ttlSeconds,
            user = currentUser(user),
        )
    }

    private fun findForLogin(username: String): Pair<AuthUser, String>? {
        val row = userMapper.selectOne(QueryWrapper<UserEntity>().eq("username", username)) ?: return null
        val role = roleMapper.selectById(row.roleCode) ?: return null
        return toAuthUser(row, role) to row.passwordHash
    }

    private fun loadUser(id: Int): AuthUser? {
        if (id <= 0) return null
        val row = userMapper.selectById(id) ?: return null
        val role = roleMapper.selectById(row.roleCode) ?: return null
        return toAuthUser(row, role)
    }

    private fun toAuthUser(row: UserEntity, role: RoleEntity): AuthUser = AuthUser(
        id = row.id,
        username = row.username,
        roleCode = row.roleCode,
        enabled = row.enabled,
        roleEnabled = role.enabled,
        tokenVersion = row.tokenVersion,
        rolePermissions = roleGrants(row.roleCode),
    )

    private fun roleGrants(code: String): Set<String> {
        if (code == Roles.ADMIN) return PermissionCatalog.defaults(code)
        return rolePermissionMapper.selectList(QueryWrapper<RolePermissionEntity>().eq("role_code", code))
            .map { it.permissionCode }
            .filterTo(linkedSetOf()) { grantable(it) }
    }

    private fun loadRolePermissions(code: String): RolePermissionsDto? {
        val role = roleMapper.selectById(code) ?: return null
        val profile = profileMapper.selectOne(QueryWrapper<RolePermissionProfileEntity>().eq("role_code", code))
        return RolePermissionsDto(
            role = role.toDto(),
            revision = profile?.revision ?: 0,
            permissions = roleGrants(code),
            protectedRole = code == Roles.ADMIN,
        )
    }

    private fun replaceRolePermissions(code: String, request: RolePermissionsRequest): RolePermissionsDto {
        if (code == Roles.ADMIN) {
            throw ApiException(403, "ADMIN role is protected", ErrorCode.ROLE_PROTECTED)
        }
        roleMapper.selectOne(QueryWrapper<RoleEntity>().eq("code", code).last("FOR UPDATE"))
            ?: throw ApiException.notFound("role not found")
        val profile = profileMapper.selectOne(
            QueryWrapper<RolePermissionProfileEntity>().eq("role_code", code).last("FOR UPDATE"),
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
            UpdateWrapper<RolePermissionProfileEntity>()
                .eq("role_code", code)
                .set("revision", revision + 1),
        )
        rolePermissionMapper.delete(QueryWrapper<RolePermissionEntity>().eq("role_code", code))
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
            UpdateWrapper<UserEntity>()
                .eq("role_code", code)
                .setSql("token_version = token_version + 1"),
        )
    }

    private fun requireEnabledRole(code: String) {
        val role = roleMapper.selectOne(QueryWrapper<RoleEntity>().eq("code", code).last("FOR UPDATE"))
        if (role == null || !role.enabled) badRequest("role must exist and be enabled")
    }

    private fun validateRole(code: String) {
        val role = roleMapper.selectById(code)
        if (role == null || !role.enabled) badRequest("role must exist and be enabled")
    }

    private fun requirePermissionAdmin(actor: AuthUser, method: String) {
        val code = "api:$method:/api/auth/roles/{code}/permissions"
        if (actor.roleCode != Roles.ADMIN || !actor.hasPermission(code)) {
            throw ApiException(403, "only ADMIN can manage permissions", ErrorCode.ADMIN_ONLY)
        }
    }

    private fun requireRoleCode(code: String): String {
        if (!ROLE_CODE_PATTERN.matches(code)) badRequest("invalid role code")
        return code
    }

    private fun validRoleName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.codePointCount(0, trimmed.length) > 50) {
            badRequest("role name must contain 1-50 characters")
        }
        return trimmed
    }

    private fun normalizeUsername(value: String): String {
        val username = value.trim().lowercase(Locale.ROOT)
        if (!USERNAME_PATTERN.matches(username)) {
            badRequest("username must contain 3-64 ASCII letters, digits, dots, underscores or hyphens")
        }
        return username
    }

    private fun validatePassword(password: String) {
        if (password.length !in 8..128 || password.isBlank()) {
            badRequest("password must contain 8-128 characters and must not be blank")
        }
    }

    private fun invalidateUsers() {
        redisCache.invalidateAfterCommit(emptyList(), listOf(CacheNames.USERS_PREFIX))
    }

    private fun invalidateRoles() {
        redisCache.invalidateAfterCommit(listOf(CacheNames.ROLES))
    }

    private fun currentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun markRetryAfter(seconds: Long) {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return
        attributes.request.setAttribute(LOGIN_RETRY_AFTER, seconds)
        attributes.response?.setHeader(HttpHeaders.RETRY_AFTER, seconds.toString())
    }

    private fun AuthUser.isProtectedAccount(): Boolean =
        username.equals(PROTECTED_USERNAME, ignoreCase = true)

    private fun AuthUser.toUserDto(): UserDto = UserDto(id, username, roleCode, enabled)

    companion object {
        const val PROTECTED_USERNAME = "admin"
        const val LOGIN_RETRY_AFTER = "hrs.login.retryAfter"
        private val USERNAME_PATTERN = Regex("[a-z0-9._-]{3,64}")
        private val ROLE_CODE_PATTERN = Regex("[A-Z][A-Z0-9_]{1,19}")

        private fun badRequest(message: String): Nothing = throw ApiException.badRequest(message)

        private fun unauthorized(): Nothing = throw ApiException(
            401,
            "invalid username or password",
            ErrorCode.INVALID_CREDENTIALS,
        )

        private fun grantable(code: String): Boolean =
            code.startsWith("page:") || PermissionCatalog.byCode[code]?.adminOnly == false

        private fun grantError(code: String, menuAdminOnlyByKey: Map<String, Boolean>): String? {
            val definition = PermissionCatalog.byCode[code]
            if (definition != null) return if (definition.adminOnly) ErrorCode.ADMIN_ONLY else null
            if (code.startsWith("page:")) {
                val adminOnly = menuAdminOnlyByKey[code.removePrefix("page:")] ?: return ErrorCode.UNKNOWN_PERMISSION
                return if (adminOnly) ErrorCode.ADMIN_ONLY else null
            }
            return ErrorCode.UNKNOWN_PERMISSION
        }
    }
}

/** 页面项固定成与 Ktor GET /api/auth/permissions 相同的字段。 */
private fun MenuPermissionDto.toPermissionDefinition(): PermissionDefinition {
    val permissionCode = if (code.startsWith("page:")) code else "page:$code"
    return PermissionDefinition(
        code = permissionCode,
        kind = "PAGE",
        label = label,
        group = group.ifEmpty { "侧栏固定" },
        path = path,
        method = method,
        adminOnly = adminOnly,
    )
}

private fun UserEntity.toDto(): UserDto = UserDto(id, username, roleCode, enabled)

private fun RoleEntity.toDto(): RoleDto = RoleDto(code, name, enabled)

private fun Throwable.isConstraintConflict(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is DataIntegrityViolationException) return true
        if ("Duplicate entry" in current.message.orEmpty()) return true
        current = current.cause
    }
    return false
}
