package com.atguigu.hr.menu

/** 仅供 OpenAPI 文档展示的菜单样例。 */

internal val sampleMenu = MenuDto(
    key = "handbook",
    title = "员工手册",
    path = "/handbook",
    icon = "BookOpen",
    group = "工作空间",
    sort = 200,
    adminOnly = false,
    builtin = false,
    enabled = true,
)
internal val sampleMenuCreate = MenuCreateRequest(
    key = "handbook",
    title = "员工手册",
    path = "/handbook",
    icon = "BookOpen",
    group = "工作空间",
    sort = 200,
)
internal val sampleMenuUpdate = MenuUpdateRequest(
    title = "员工手册",
    path = "/handbook",
    icon = "BookOpen",
    group = "工作空间",
    sort = 200,
)
