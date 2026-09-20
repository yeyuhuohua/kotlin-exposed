package com.atguigu.hr.auth

/** 仅供 OpenAPI 文档展示的认证与角色权限样例，不参与数据库初始化或业务计算。 */

internal val sampleUser = UserDto(1, "admin", "ADMIN", true)
internal val sampleLogin = LoginRequest("admin", "replace-with-your-password")
internal val sampleUserCreate = UserCreateRequest("reader", "replace-with-a-strong-password", "READER")
internal val sampleUserUpdate = UserUpdateRequest(enabled = false)
internal val sampleCurrentUser = CurrentUserDto(1, "admin", "ADMIN", true, PermissionCatalog.defaults("ADMIN"))
internal val sampleToken = TokenDto("<access-token>", 3600, sampleCurrentUser)
internal val samplePermissions = RolePermissionsDto(
    RoleDto("READER", "普通用户", true), 1,
    setOf("page:employees", "api:GET:/api/employees"), false,
)
