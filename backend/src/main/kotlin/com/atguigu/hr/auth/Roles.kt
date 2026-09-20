package com.atguigu.hr.auth

import org.jetbrains.exposed.v1.core.Table

/** 认证与角色权限表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Roles : Table("auth_roles") {
    val code = varchar("code", 20)
    val name = varchar("name", 50)
    val enabled = bool("enabled").default(true)
    override val primaryKey = PrimaryKey(code)
}
