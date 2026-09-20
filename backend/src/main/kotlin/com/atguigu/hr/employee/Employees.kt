package com.atguigu.hr.employee

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date

/** 员工表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Employees : Table("employees") { // 员工
    val employeeId = integer("employee_id")
    val firstName = varchar("first_name", 20).nullable()
    val lastName = varchar("last_name", 25)
    val email = varchar("email", 25)
    val phoneNumber = varchar("phone_number", 20).nullable()
    val hireDate = date("hire_date")
    val jobId = varchar("job_id", 10)
    val salary = double("salary").nullable()
    val commissionPct = double("commission_pct").nullable()
    val managerId = integer("manager_id").nullable()
    val departmentId = integer("department_id").nullable()
    override val primaryKey = PrimaryKey(employeeId)
}
