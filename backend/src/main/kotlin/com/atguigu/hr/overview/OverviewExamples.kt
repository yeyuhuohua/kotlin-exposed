package com.atguigu.hr.overview

import com.atguigu.hr.department.sampleDepartment
import com.atguigu.hr.employee.sampleEmployee
import com.atguigu.hr.geography.sampleRegion

/** 仅供 OpenAPI 文档展示的概览样例，不参与数据库初始化或业务计算。 */

internal val sampleOverview = OverviewDto(
    fetchedWith = "kotlinx.coroutines.async",
    elapsedMs = 34,
    employeeTotal = 107,
    departmentCount = 27,
    jobCount = 19,
    locationCount = 23,
    regionCount = 4,
    tEmpCount = 8,
    sampleEmployees = listOf(sampleEmployee),
    departments = listOf(sampleDepartment),
    regions = listOf(sampleRegion),
)
