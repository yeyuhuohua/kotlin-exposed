package com.atguigu.hr.demo.temp

import org.jetbrains.exposed.v1.core.Table

/** 列名 deptId 为驼峰。 */
object TEmp : Table("t_emp") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 20).nullable()
    val age = integer("age").nullable()
    val deptId = integer("deptId").nullable()
    val empno = integer("empno")
    override val primaryKey = PrimaryKey(id)
}
