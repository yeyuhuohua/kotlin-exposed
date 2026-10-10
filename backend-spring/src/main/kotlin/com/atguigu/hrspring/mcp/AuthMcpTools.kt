package com.atguigu.hrspring.mcp
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
import com.atguigu.hrspring.dto.PageRoute
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/** 认证、用户、角色与权限的 MCP 工具。调用方已由 /mcp 过滤器限制为 ADMIN。 */
@Component
class AuthMcpTools(
    private val authService: AuthService,
) {
    @McpTool(name = "login", description = "使用用户名和密码登录并签发访问 Token。用户名会去掉首尾空白并转为小写。")
    fun login(
        @McpToolParam(description = "用户名", required = true) username: String,
        @McpToolParam(description = "密码，1 到 128 个字符", required = true) password: String,
    ): TokenDto = authService.login(LoginRequest(username, password))

    @McpTool(name = "get_current_user", description = "查询当前登录用户、角色和有效权限，包含仍启用的页面权限码。")
    fun getCurrentUser(): CurrentUserDto = authService.currentUser(CurrentUserHolder.require())

    @McpTool(name = "logout", description = "退出登录，并撤销当前账号在所有设备上的 Token。")
    fun logout(): String {
        authService.logout(CurrentUserHolder.require().id)
        return "logged out on all devices"
    }

    @McpTool(name = "list_page_routes", description = "查询当前用户可访问的页面清单，供前端注册路由和菜单。")
    fun listPageRoutes(): List<PageRoute> = authService.pageRoutes(CurrentUserHolder.require())

    @McpTool(name = "list_users", description = "分页查询用户。每页 1 到 200 条，默认 50；offset 为跳过的条数，默认 0。")
    fun listUsers(
        @McpToolParam(description = "每页条数，默认 50，最大 200", required = false) limit: Int? = null,
        @McpToolParam(description = "跳过条数，默认 0", required = false) offset: Long? = null,
    ): ApiList<UserDto> = authService.listUsers(limit, offset).first

    @McpTool(name = "create_user", description = "创建用户。用户名 3 到 64 位，密码 8 到 128 位。不能使用保留用户名 admin。")
    fun createUser(
        @McpToolParam(description = "用户名", required = true) username: String,
        @McpToolParam(description = "密码，8 到 128 个字符", required = true) password: String,
        @McpToolParam(description = "角色编码，省略时为 READER", required = false) roleCode: String? = null,
    ): UserDto = authService.createUser(UserCreateRequest(username, password, roleCode))

    @McpTool(
        name = "update_user",
        description = "修改用户的角色、启停或密码，并撤销该用户已有 Token。不能停用或降级自己，也不能改内置 admin。",
    )
    fun updateUser(
        @McpToolParam(description = "用户编号", required = true) id: Int,
        @McpToolParam(description = "新角色编码，不修改则省略", required = false) roleCode: String? = null,
        @McpToolParam(description = "是否启用，不修改则省略", required = false) enabled: Boolean? = null,
        @McpToolParam(description = "新密码，8 到 128 个字符，不修改则省略", required = false) password: String? = null,
    ): UserDto = authService.updateUser(
        CurrentUserHolder.require().id,
        id,
        UserUpdateRequest(roleCode, enabled, password),
    )

    @McpTool(name = "delete_user", description = "删除用户。不能删除自己，也不能删除内置 admin。")
    fun deleteUser(
        @McpToolParam(description = "用户编号", required = true) id: Int,
    ): String {
        if (!authService.deleteUser(CurrentUserHolder.require().id, id)) {
            throw ApiException.notFound("user not found")
        }
        return "deleted"
    }

    @McpTool(name = "list_roles", description = "查询全部角色，按角色编码排序。")
    fun listRoles(): List<RoleDto> = authService.listRoles().first

    @McpTool(
        name = "create_role",
        description = "创建角色，初始业务权限为空。编码为 2 到 20 位大写字母、数字或下划线，并以字母开头。",
    )
    fun createRole(
        @McpToolParam(description = "角色编码", required = true) code: String,
        @McpToolParam(description = "角色名称，1 到 50 个字符", required = true) name: String,
    ): RoleDto = authService.createRole(RoleCreateRequest(code, name))

    @McpTool(name = "update_role", description = "修改角色名称或启停状态。ADMIN 角色受保护。停用或重新启用会撤销该角色用户的 Token。")
    fun updateRole(
        @McpToolParam(description = "角色编码", required = true) code: String,
        @McpToolParam(description = "新名称，不修改则省略", required = false) name: String? = null,
        @McpToolParam(description = "是否启用，不修改则省略", required = false) enabled: Boolean? = null,
    ): RoleDto = authService.updateRole(code, RoleUpdateRequest(name, enabled))

    @McpTool(name = "delete_role", description = "删除角色及其权限。ADMIN 角色受保护；仍有用户引用该角色时失败。")
    fun deleteRole(
        @McpToolParam(description = "角色编码", required = true) code: String,
    ): String {
        if (!authService.deleteRole(code)) throw ApiException.notFound("role not found")
        return "deleted"
    }

    @McpTool(name = "list_permissions", description = "查询权限目录。页面权限在前，接口权限在后。")
    fun listPermissions(): List<PermissionDefinition> = authService.listPermissions()

    @McpTool(name = "get_role_permissions", description = "查看角色已授予的页面和接口权限，以及保存时要用的版本号。")
    fun getRolePermissions(
        @McpToolParam(description = "角色编码", required = true) code: String,
    ): RolePermissionsDto = authService.getRolePermissions(CurrentUserHolder.require(), code)

    @McpTool(
        name = "update_role_permissions",
        description = "按版本号整表替换角色权限，并撤销该角色所有用户的 Token。版本不一致时需要先重新查询。ADMIN 角色不可修改。",
    )
    fun updateRolePermissions(
        @McpToolParam(description = "角色编码", required = true) code: String,
        @McpToolParam(description = "GET 返回的权限版本号", required = true) revision: Int,
        @McpToolParam(description = "权限码列表，空列表表示清空可配置权限", required = true) permissions: List<String>,
    ): RolePermissionsDto = authService.updateRolePermissions(
        CurrentUserHolder.require(),
        code,
        RolePermissionsRequest(revision, permissions.toCollection(linkedSetOf())),
    )
}
