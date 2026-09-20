package com.atguigu.hr.health

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** 健康检查。database / redis 各自 UP 或 DOWN。 */
@Serializable
@JsonSchema.Description("健康检查。database / redis 各自 UP 或 DOWN")
data class HealthDto(
    @JsonSchema.Description("总体状态，MySQL 与 Redis 都通为 UP")
    val status: String,
    @JsonSchema.Description("MySQL atguigudb 连通性")
    val database: String,
    @JsonSchema.Description("Redis PING 结果")
    val redis: String,
)
