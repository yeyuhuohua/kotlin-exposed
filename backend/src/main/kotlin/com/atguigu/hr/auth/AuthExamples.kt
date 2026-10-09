package com.atguigu.hr.auth

import com.atguigu.hr.menu.PageRoute

/** 仅供 OpenAPI 文档展示的认证与角色权限样例，不参与数据库初始化或业务计算。 */

internal val sampleUser = UserDto(1, "admin", "ADMIN", true)
internal val sampleLogin = LoginRequest("admin", "replace-with-your-password")
internal val sampleUserCreate = UserCreateRequest("reader", "replace-with-a-strong-password", "READER")
internal val sampleUserUpdate = UserUpdateRequest(enabled = false)
internal val sampleCurrentUser = CurrentUserDto(
    1, "admin", "ADMIN", true,
    PermissionCatalog.defaults("ADMIN") + setOf("page:overview", "page:employees", "page:users"),
)
internal val sampleToken = TokenDto("<access-token>", 3600, sampleCurrentUser)
internal val samplePermissions = RolePermissionsDto(
    RoleDto("READER", "普通用户", true), 1,
    setOf("page:employees", "api:GET:/api/employees"), false,
)
internal val samplePageRoutes = listOf(
    PageRoute("overview", "工作概览", "/", "LayoutDashboard", "工作空间", 10),
    PageRoute("employees", "员工管理", "/employees", "Users", "工作空间", 20),
    PageRoute("users", "用户管理", "/users", "ShieldCheck", "访问控制", 110, adminOnly = true),
)
