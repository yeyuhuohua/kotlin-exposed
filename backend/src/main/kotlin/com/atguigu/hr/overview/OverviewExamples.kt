package com.atguigu.hr.overview

import com.atguigu.hr.department.sampleDepartment
import com.atguigu.hr.employee.DepartmentHeadcountDto
import com.atguigu.hr.employee.SalarySummaryDto
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
    departmentHeadcount = listOf(
        DepartmentHeadcountDto(departmentId = 90, departmentName = "Executive", count = 3),
        DepartmentHeadcountDto(departmentId = 60, departmentName = "IT", count = 5),
        DepartmentHeadcountDto(departmentId = null, departmentName = "未分配部门", count = 1),
    ),
    salarySummary = SalarySummaryDto(
        employeesWithSalary = 107,
        totalSalary = 691_416.0,
        averageSalary = 6_461.83,
        minSalary = 2_100.0,
        maxSalary = 24_000.0,
    ),
)
