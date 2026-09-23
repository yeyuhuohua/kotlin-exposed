package com.atguigu.hr.audit

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

/** 审计日志的 Exposed 映射：登录尝试与接口调用各一张表，只增不改。 */

/** 登录尝试记录：成功与失败都写，失败带原因码（invalid_credentials / rate_limited）。 */
object LoginRecords : Table("audit_login_records") {
    val id = long("id").autoIncrement()
    val username = varchar("username", 64)
    val userId = integer("user_id").nullable()
    val ip = varchar("ip", 45)
    val userAgent = varchar("user_agent", 255).nullable()
    val success = bool("success")
    val errorCode = varchar("error_code", 40).nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, createdAt)
        index(isUnique = false, username)
    }
}

/** /api 调用记录：未认证请求（如登录本身）userId/username 为 null。 */
object ApiCallRecords : Table("audit_api_calls") {
    val id = long("id").autoIncrement()
    val userId = integer("user_id").nullable()
    val username = varchar("username", 64).nullable()
    val method = varchar("method", 8)
    val path = varchar("path", 255)
    val queryString = varchar("query_string", 255).nullable()
    val statusCode = integer("status_code")
    val durationMs = long("duration_ms")
    val ip = varchar("ip", 45)
    val userAgent = varchar("user_agent", 255).nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, createdAt)
        index(isUnique = false, method, path)
    }
}
