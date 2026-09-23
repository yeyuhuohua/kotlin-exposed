package com.atguigu.hr.audit

import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.config.DatabaseFactory.dbQuery
import com.atguigu.hr.config.DatabaseFactory.dbUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.time.LocalDateTime

/** 审计表的写入与分页查询；两张表都只插入，不提供修改。 */
object AuditRepository {

    /** 幂等建表，与认证表同一时机执行。 */
    suspend fun initialize() = dbUpdate {
        SchemaUtils.create(LoginRecords, ApiCallRecords)
    }

    suspend fun insertLogin(
        username: String,
        userId: Int?,
        ip: String,
        userAgent: String?,
        success: Boolean,
        errorCode: String?,
    ) = dbUpdate {
        LoginRecords.insert {
            it[LoginRecords.username] = username.take(64)
            it[LoginRecords.userId] = userId
            it[LoginRecords.ip] = ip.take(45)
            it[LoginRecords.userAgent] = userAgent?.take(255)
            it[LoginRecords.success] = success
            it[LoginRecords.errorCode] = errorCode?.take(40)
            it[createdAt] = LocalDateTime.now()
        }
    }

    suspend fun insertApiCall(
        userId: Int?,
        username: String?,
        method: String,
        path: String,
        queryString: String?,
        statusCode: Int,
        durationMs: Long,
        ip: String,
        userAgent: String?,
    ) = dbUpdate {
        ApiCallRecords.insert {
            it[ApiCallRecords.userId] = userId
            it[ApiCallRecords.username] = username?.take(64)
            it[ApiCallRecords.method] = method.take(8)
            it[ApiCallRecords.path] = path.take(255)
            it[ApiCallRecords.queryString] = queryString?.take(255)
            it[ApiCallRecords.statusCode] = statusCode
            it[ApiCallRecords.durationMs] = durationMs
            it[ApiCallRecords.ip] = ip.take(45)
            it[ApiCallRecords.userAgent] = userAgent?.take(255)
            it[createdAt] = LocalDateTime.now()
        }
    }

    suspend fun listLogins(
        limit: Int,
        offset: Long,
        username: String?,
        success: Boolean?,
    ): ApiList<LoginRecordDto> = dbQuery {
        val query = LoginRecords.selectAll()
        username?.takeIf { it.isNotBlank() }?.let { query.andWhere { LoginRecords.username like "%${it.trim()}%" } }
        success?.let { query.andWhere { LoginRecords.success eq it } }
        val total = query.count()
        val items = query
            .orderBy(LoginRecords.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toLoginRecord() }
            .toList()
        ApiList(total = total, items = items)
    }

    suspend fun listApiCalls(
        limit: Int,
        offset: Long,
        username: String?,
        method: String?,
        path: String?,
    ): ApiList<ApiCallRecordDto> = dbQuery {
        val query = ApiCallRecords.selectAll()
        username?.takeIf { it.isNotBlank() }?.let { query.andWhere { ApiCallRecords.username like "%${it.trim()}%" } }
        method?.takeIf { it.isNotBlank() }?.let { query.andWhere { ApiCallRecords.method eq it.trim().uppercase() } }
        // 路径前缀匹配（如 /api/employees），可以走 (method, path) 索引方向
        path?.takeIf { it.isNotBlank() }?.let { query.andWhere { ApiCallRecords.path like "${it.trim()}%" } }
        val total = query.count()
        val items = query
            .orderBy(ApiCallRecords.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toApiCallRecord() }
            .toList()
        ApiList(total = total, items = items)
    }
}

private fun ResultRow.toLoginRecord() = LoginRecordDto(
    id = this[LoginRecords.id],
    username = this[LoginRecords.username],
    userId = this[LoginRecords.userId],
    ip = this[LoginRecords.ip],
    userAgent = this[LoginRecords.userAgent],
    success = this[LoginRecords.success],
    errorCode = this[LoginRecords.errorCode],
    createdAt = this[LoginRecords.createdAt].toString(),
)

private fun ResultRow.toApiCallRecord() = ApiCallRecordDto(
    id = this[ApiCallRecords.id],
    userId = this[ApiCallRecords.userId],
    username = this[ApiCallRecords.username],
    method = this[ApiCallRecords.method],
    path = this[ApiCallRecords.path],
    queryString = this[ApiCallRecords.queryString],
    statusCode = this[ApiCallRecords.statusCode],
    durationMs = this[ApiCallRecords.durationMs],
    ip = this[ApiCallRecords.ip],
    userAgent = this[ApiCallRecords.userAgent],
    createdAt = this[ApiCallRecords.createdAt].toString(),
)
