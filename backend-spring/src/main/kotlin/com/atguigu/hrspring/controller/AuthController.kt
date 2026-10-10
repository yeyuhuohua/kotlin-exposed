package com.atguigu.hrspring.controller
import com.atguigu.hrspring.auth.CurrentUserDto
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.PermissionDefinition
import com.atguigu.hrspring.auth.RoleDto
import com.atguigu.hrspring.auth.RolePermissionsDto
import com.atguigu.hrspring.auth.TokenDto
import com.atguigu.hrspring.auth.UserDto
import com.atguigu.hrspring.dto.LoginRequest
import com.atguigu.hrspring.dto.RoleCreateRequest
import com.atguigu.hrspring.dto.RolePermissionsRequest
import com.atguigu.hrspring.dto.RoleUpdateRequest
import com.atguigu.hrspring.dto.UserCreateRequest
import com.atguigu.hrspring.dto.UserUpdateRequest
import com.atguigu.hrspring.service.AuthService
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.common.api.created
import com.atguigu.hrspring.common.api.ok
import com.atguigu.hrspring.common.api.updated
import com.atguigu.hrspring.dto.PageRoute
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 认证、用户和角色管理。管理接口由权限拦截器限制为 ADMIN。 */
@Tag(name = "auth")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
) {
    @Operation(summary = "使用数据库账号登录并获取访问 Token")
    @PostMapping("/login")
    fun login(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ApiResult<TokenDto>> {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        if (request.contentLengthLong > MAX_LOGIN_BODY_BYTES) {
            throw ApiException(413, "request body too large")
        }
        val bytes = request.inputStream.readNBytes(MAX_LOGIN_BODY_BYTES.toInt() + 1)
        if (bytes.size > MAX_LOGIN_BODY_BYTES) {
            throw ApiException(413, "request body too large")
        }
        val body = try {
            objectMapper.readValue(bytes, LoginRequest::class.java)
        } catch (ex: Exception) {
            throw ApiException.badRequest("invalid request body")
        }
        return try {
            val token = authService.login(body)
            ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ok(token))
        } catch (ex: ApiException) {
            if (ex.status != 429) throw ex
            val retryAfter = request.getAttribute(AuthService.LOGIN_RETRY_AFTER)?.toString()
            val entity = ResponseEntity.status(429)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
            if (retryAfter != null) entity.header(HttpHeaders.RETRY_AFTER, retryAfter)
            entity.body(ApiResult(429, ex.message, null, ex.resolvedError))
        }
    }

    @Operation(summary = "查询当前用户、角色及有效权限")
    @GetMapping("/me")
    fun me(response: HttpServletResponse): ApiResult<CurrentUserDto> {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        return ok(authService.currentUser(CurrentUserHolder.require()))
    }

    @Operation(summary = "退出登录并撤销当前账号在所有设备上的 Token")
    @PostMapping("/logout")
    fun logout(): ApiResult<String> {
        authService.logout(CurrentUserHolder.require().id)
        return ok("logged out on all devices")
    }

    @Operation(summary = "查询当前用户可访问的页面清单（前端据此动态注册路由与菜单；页面来自菜单表）")
    @GetMapping("/routes")
    fun routes(): ResponseEntity<ApiResult<List<PageRoute>>> =
        noStore(cachedOk(authService.pageRoutes(CurrentUserHolder.require()), false))

    @Operation(summary = "分页查询用户（仅 ADMIN）")
    @GetMapping("/users")
    fun listUsers(
        @Parameter(description = "每页条数，默认 50，最大 200")
        @RequestParam(required = false) limit: Int?,
        @Parameter(description = "跳过条数，默认 0")
        @RequestParam(required = false) offset: Long?,
    ): ResponseEntity<ApiResult<ApiList<UserDto>>> {
        val (data, hit) = authService.listUsers(limit, offset)
        return noStore(cachedOk(data, hit))
    }

    @Operation(summary = "创建用户（仅 ADMIN；不提供公开注册）")
    @PostMapping("/users")
    fun createUser(@RequestBody request: UserCreateRequest): ResponseEntity<ApiResult<UserDto>> =
        ResponseEntity.ok(created(authService.createUser(request)))

    @Operation(summary = "调整角色、启停账号或重置密码，并撤销已有 Token（仅 ADMIN）")
    @PutMapping("/users/{id}")
    fun updateUser(
        @Parameter(description = "用户编号")
        @PathVariable id: String,
        @RequestBody request: UserUpdateRequest,
    ): ResponseEntity<ApiResult<UserDto>> =
        ResponseEntity.ok(
            updated(authService.updateUser(CurrentUserHolder.require().id, userId(id), request)),
        )

    @Operation(summary = "删除账号（仅 ADMIN；admin 账号与当前登录账号不可删除）")
    @DeleteMapping("/users/{id}")
    fun deleteUser(
        @Parameter(description = "用户编号")
        @PathVariable id: String,
    ): ApiResult<String> {
        if (!authService.deleteUser(CurrentUserHolder.require().id, userId(id))) {
            throw ApiException.notFound("user not found")
        }
        return ok("deleted")
    }

    @Operation(summary = "查询角色列表（仅 ADMIN）")
    @GetMapping("/roles")
    fun listRoles(): ResponseEntity<ApiResult<List<RoleDto>>> {
        val (data, hit) = authService.listRoles()
        return cachedOk(data, hit)
    }

    @Operation(summary = "创建角色（仅 ADMIN；初始业务权限为空）")
    @PostMapping("/roles")
    fun createRole(@RequestBody request: RoleCreateRequest): ResponseEntity<ApiResult<RoleDto>> =
        ResponseEntity.ok(created(authService.createRole(request)))

    @Operation(summary = "修改角色名称或状态（仅 ADMIN；ADMIN 角色受保护）")
    @PutMapping("/roles/{code}")
    fun updateRole(
        @Parameter(description = "角色编码")
        @PathVariable code: String,
        @RequestBody request: RoleUpdateRequest,
    ): ResponseEntity<ApiResult<RoleDto>> =
        ResponseEntity.ok(updated(authService.updateRole(code, request)))

    @Operation(summary = "删除角色（仅 ADMIN；ADMIN 角色受保护，仍有账号引用时返回 409）")
    @DeleteMapping("/roles/{code}")
    fun deleteRole(
        @Parameter(description = "角色编码")
        @PathVariable code: String,
    ): ApiResult<String> {
        if (!authService.deleteRole(code)) throw ApiException.notFound("role not found")
        return ok("deleted")
    }

    @Operation(summary = "查看角色的页面与接口权限（仅 ADMIN）")
    @GetMapping("/roles/{code}/permissions")
    fun getRolePermissions(
        @Parameter(description = "角色编码")
        @PathVariable code: String,
    ): ResponseEntity<ApiResult<RolePermissionsDto>> =
        noStore(cachedOk(authService.getRolePermissions(CurrentUserHolder.require(), code), false))

    @Operation(summary = "替换角色权限并撤销该角色所有用户的 Token（仅 ADMIN）")
    @PutMapping("/roles/{code}/permissions")
    fun updateRolePermissions(
        @Parameter(description = "角色编码")
        @PathVariable code: String,
        @RequestBody request: RolePermissionsRequest,
    ): ResponseEntity<ApiResult<RolePermissionsDto>> =
        ResponseEntity.ok(updated(authService.updateRolePermissions(CurrentUserHolder.require(), code, request)))

    @Operation(summary = "查询权限目录（仅 ADMIN）：页面部分来自菜单表，接口部分为后端登记")
    @GetMapping("/permissions")
    fun permissions(): ResponseEntity<ApiResult<List<PermissionDefinition>>> =
        noStore(cachedOk(authService.listPermissions(), false))

    private fun userId(raw: String): Int =
        raw.toIntOrNull()?.takeIf { it > 0 } ?: throw ApiException.badRequest("invalid user id")

    private fun <T> noStore(response: ResponseEntity<ApiResult<T>>): ResponseEntity<ApiResult<T>> {
        val payload = response.body ?: throw IllegalStateException("missing response body")
        return ResponseEntity.status(response.statusCode)
            .headers(response.headers)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(payload)
    }

    companion object {
        private const val MAX_LOGIN_BODY_BYTES = 4 * 1024
    }
}
