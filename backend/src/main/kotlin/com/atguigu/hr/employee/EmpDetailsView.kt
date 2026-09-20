package com.atguigu.hr.employee

import org.jetbrains.exposed.v1.core.Table

/** 库中的员工详情视图，可当表查询。 */
object EmpDetailsView : Table("emp_details_view") {
    val employeeId = integer("employee_id")
    val jobId = varchar("job_id", 10)
    val managerId = integer("manager_id").nullable()
    val departmentId = integer("department_id").nullable()
    val locationId = integer("location_id").nullable()
    val countryId = char("country_id", 2).nullable()
    val firstName = varchar("first_name", 20).nullable()
    val lastName = varchar("last_name", 25)
    val salary = double("salary").nullable()
    val commissionPct = double("commission_pct").nullable()
    val departmentName = varchar("department_name", 30)
    val jobTitle = varchar("job_title", 35)
    val city = varchar("city", 30)
    val stateProvince = varchar("state_province", 25).nullable()
    val countryName = varchar("country_name", 40).nullable()
    val regionName = varchar("region_name", 25).nullable()
}
