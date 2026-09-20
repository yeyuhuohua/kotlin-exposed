package com.atguigu.hr.department

import org.jetbrains.exposed.v1.core.Table

/** 部门表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Departments : Table("departments") { // 部门
    val departmentId = integer("department_id")
    val departmentName = varchar("department_name", 30)
    val managerId = integer("manager_id").nullable()
    val locationId = integer("location_id").nullable()
    override val primaryKey = PrimaryKey(departmentId)
}
