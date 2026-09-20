package com.atguigu.hr.common.api

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/**
 * 统一接口信封。成功 code=200；失败 code 与 HTTP 状态码一致，data 为 null。
 */
@Serializable
@JsonSchema.Description("统一响应信封")
data class ApiResult<T>(
    @JsonSchema.Description("业务状态码，成功 200")
    val code: Int,
    @JsonSchema.Description("提示信息")
    val message: String,
    @JsonSchema.Description("业务数据，失败时为 null")
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T, message: String = "ok"): ApiResult<T> =
            ApiResult(code = 200, message = message, data = data)

        fun fail(code: Int, message: String): ApiResult<String?> =
            ApiResult(code = code, message = message, data = null)
    }
}
