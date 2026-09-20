package com.atguigu.hr.job

import org.jetbrains.exposed.v1.core.Table

/** 岗位表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Jobs : Table("jobs") { // 岗位
    val jobId = varchar("job_id", 10)
    val jobTitle = varchar("job_title", 35)
    val minSalary = integer("min_salary").nullable()
    val maxSalary = integer("max_salary").nullable()
    override val primaryKey = PrimaryKey(jobId)
}
