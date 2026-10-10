package com.atguigu.hrspring.dto
/** 员工。hireDate 为 ISO 日期字符串 yyyy-MM-dd。 */
data class EmployeeDto(
    val employeeId: Int,
    val firstName: String?,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
    val hireDate: String,
    val jobId: String,
    val salary: Double?,
    val commissionPct: Double?,
    val managerId: Int?,
    val departmentId: Int?,
)

/** 员工详情。视图行没有 email / phoneNumber / hireDate，这三个字段为 null。 */
data class EmployeeDetailDto(
    val employeeId: Int,
    val firstName: String?,
    val lastName: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val hireDate: String? = null,
    val jobId: String,
    val jobTitle: String,
    val salary: Double?,
    val commissionPct: Double?,
    val managerId: Int?,
    val departmentId: Int?,
    val departmentName: String,
    val locationId: Int?,
    val city: String,
    val stateProvince: String?,
    val countryId: String?,
    val countryName: String?,
    val regionName: String?,
)

/** 未分配部门的显示名，聚合查询和前端展示共用。 */
const val UNASSIGNED_DEPARTMENT = "未分配部门"

/** 按部门统计的员工人数。departmentId 为 null 表示未分配部门。 */
data class DepartmentHeadcountDto(
    val departmentId: Int?,
    val departmentName: String,
    val count: Long,
)

/** 员工薪资汇总，全部由数据库聚合得出。 */
data class SalarySummaryDto(
    val employeesWithSalary: Long,
    val totalSalary: Double,
    val averageSalary: Double?,
    val minSalary: Double?,
    val maxSalary: Double?,
)
