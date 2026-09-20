package com.atguigu.hr.auth

import org.jetbrains.exposed.v1.core.Table

/** 认证与角色权限表的 Exposed 映射；字段类型、长度和可空性对应现有 MySQL 结构。 */

object Users : Table("auth_users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val roleCode = varchar("role_code", 20).references(Roles.code)
    val enabled = bool("enabled").default(true)
    val tokenVersion = integer("token_version").default(0)
    override val primaryKey = PrimaryKey(id)
}
