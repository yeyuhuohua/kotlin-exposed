package com.atguigu.hr.audit

import kotlinx.serialization.Serializable

/** 登录记录视图。createdAt 为 ISO 字符串。 */
@Serializable
data class LoginRecordDto(
    val id: Long,
    val username: String,
    val userId: Int?,
    val ip: String,
    val userAgent: String?,
    val success: Boolean,
    val errorCode: String?,
    val createdAt: String,
)

/** 接口调用记录视图。 */
@Serializable
data class ApiCallRecordDto(
    val id: Long,
    val userId: Int?,
    val username: String?,
    val method: String,
    val path: String,
    val queryString: String?,
    val statusCode: Int,
    val durationMs: Long,
    val ip: String,
    val userAgent: String?,
    val createdAt: String,
)
