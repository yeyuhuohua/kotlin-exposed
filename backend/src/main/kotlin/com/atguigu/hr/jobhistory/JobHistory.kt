package com.atguigu.hr.jobhistory

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date

/** 任职历史表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object JobHistory : Table("job_history") { // 任职历史
    val employeeId = integer("employee_id")
    val startDate = date("start_date")
    val endDate = date("end_date")
    val jobId = varchar("job_id", 10)
    val departmentId = integer("department_id").nullable()
    override val primaryKey = PrimaryKey(employeeId, startDate)
}
