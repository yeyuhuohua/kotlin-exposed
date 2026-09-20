package com.atguigu.hr.employee

/** 仅供 OpenAPI 文档展示的员工样例，不参与数据库初始化或业务计算。 */

internal val sampleEmployee = EmployeeDto(
    employeeId = 100,
    firstName = "Steven",
    lastName = "King",
    email = "SKING",
    phoneNumber = "515.123.4567",
    hireDate = "1987-06-17",
    jobId = "AD_PRES",
    salary = 24000.0,
    commissionPct = null,
    managerId = null,
    departmentId = 90,
)

internal val sampleEmployeeDetail = EmployeeDetailDto(
    employeeId = 100,
    firstName = "Steven",
    lastName = "King",
    email = "SKING",
    phoneNumber = "515.123.4567",
    hireDate = "1987-06-17",
    jobId = "AD_PRES",
    jobTitle = "President",
    salary = 24000.0,
    commissionPct = null,
    managerId = null,
    departmentId = 90,
    departmentName = "Executive",
    locationId = 1700,
    city = "Seattle",
    stateProvince = "Washington",
    countryId = "US",
    countryName = "United States of America",
    regionName = "Americas",
)

internal val sampleEmployeePatch = EmployeeUpdateRequest(
    salary = 24000.0,
    departmentId = 90,
    jobId = "AD_PRES",
    phoneNumber = "515.123.4567",
)

internal val sampleEmployeeCreate = EmployeeCreateRequest(
    employeeId = 999,
    firstName = "New",
    lastName = "Hire",
    email = "NHIRE999",
    phoneNumber = "515.000.0000",
    hireDate = "2026-09-18",
    jobId = "IT_PROG",
    salary = 5000.0,
    departmentId = 60,
)
