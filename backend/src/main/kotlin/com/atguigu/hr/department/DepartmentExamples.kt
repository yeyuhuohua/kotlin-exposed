package com.atguigu.hr.department

/** 仅供 OpenAPI 文档展示的部门样例，不参与数据库初始化或业务计算。 */

internal val sampleDepartment = DepartmentDto(90, "Executive", 100, 1700)
internal val sampleDepartmentPatch = DepartmentUpdateRequest(
    departmentName = "Executive",
    managerId = 100,
    locationId = 1700,
)
internal val sampleDepartmentCreate = DepartmentCreateRequest(
    departmentId = 280,
    departmentName = "Kotlin Lab",
    managerId = 100,
    locationId = 1700,
)
