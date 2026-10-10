package com.atguigu.hrspring.dto

/** /api/overview 聚合结果。 */
data class OverviewDto(
    val fetchedWith: String,
    val elapsedMs: Long,
    val employeeTotal: Long,
    val departmentCount: Int,
    val jobCount: Int,
    val locationCount: Int,
    val regionCount: Int,
    val tEmpCount: Int,
    val sampleEmployees: List<EmployeeDto>,
    val departments: List<DepartmentDto>,
    val regions: List<RegionDto>,
    /** 服务端聚合的部门人数分布，前端不再拉全量员工自行统计。 */
    val departmentHeadcount: List<DepartmentHeadcountDto>,
    /** 服务端聚合的薪资汇总。 */
    val salarySummary: SalarySummaryDto,
)
