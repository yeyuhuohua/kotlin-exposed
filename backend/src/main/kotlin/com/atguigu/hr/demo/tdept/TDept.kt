package com.atguigu.hr.demo.tdept

import org.jetbrains.exposed.v1.core.Table

/** 列名是驼峰 deptName / address，与库表一致。 */
object TDept : Table("t_dept") {
    val id = integer("id").autoIncrement()
    val deptName = varchar("deptName", 30).nullable()
    val address = varchar("address", 30).nullable()
    override val primaryKey = PrimaryKey(id)
}
