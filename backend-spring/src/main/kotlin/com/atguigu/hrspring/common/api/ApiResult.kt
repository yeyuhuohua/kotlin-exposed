package com.atguigu.hrspring.common.api

/**
 * 统一响应信封,与 Ktor 后端契约一致:成功 code=200、error=null;失败 data=null、error 为稳定机器可读码。
 */
data class ApiResult<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
    val error: String? = null,
)

data class ApiList<T>(
    val total: Long,
    val items: List<T>,
)

object ErrorCode {
    const val VALIDATION_FAILED = "validation_failed"
    const val UNAUTHORIZED = "unauthorized"
    const val INVALID_CREDENTIALS = "invalid_credentials"
    const val FORBIDDEN = "forbidden"
    const val ADMIN_ONLY = "admin_only"
    const val ROLE_PROTECTED = "role_protected"
    const val ADMIN_ACCOUNT_PROTECTED = "admin_account_protected"
    const val SELF_DEMOTION = "self_demotion"
    const val SELF_DELETION = "self_deletion"
    const val ROLE_IN_USE = "role_in_use"
    const val NOT_FOUND = "not_found"
    const val CONFLICT = "conflict"
    const val REVISION_CONFLICT = "revision_conflict"
    const val USERNAME_TAKEN = "username_taken"
    const val UNKNOWN_PERMISSION = "unknown_permission"
    const val UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL_ERROR = "internal_error"
    const val DEPENDENCY_UNAVAILABLE = "dependency_unavailable"

    fun forStatus(status: Int): String = when (status) {
        400 -> VALIDATION_FAILED
        401 -> UNAUTHORIZED
        403 -> FORBIDDEN
        404 -> NOT_FOUND
        409 -> CONFLICT
        429 -> RATE_LIMITED
        500 -> INTERNAL_ERROR
        503 -> DEPENDENCY_UNAVAILABLE
        else -> "error"
    }
}

/**
 * 业务异常:抛出后由全局处理器转为统一信封,HTTP 状态与 code 一致。
 */
class ApiException(
    val status: Int,
    override val message: String,
    val error: String? = null,
) : RuntimeException(message) {

    val resolvedError: String get() = error ?: ErrorCode.forStatus(status)

    companion object {
        fun badRequest(message: String, error: String? = null) = ApiException(400, message, error)

        fun unauthorized(message: String = "authentication required", error: String? = null) =
            ApiException(401, message, error ?: ErrorCode.UNAUTHORIZED)

        fun forbidden(message: String = "insufficient permissions", error: String? = null) =
            ApiException(403, message, error ?: ErrorCode.FORBIDDEN)

        fun notFound(message: String = "not found") = ApiException(404, message, ErrorCode.NOT_FOUND)

        fun conflict(message: String, error: String? = null) = ApiException(409, message, error ?: ErrorCode.CONFLICT)
    }
}

fun <T> ok(data: T): ApiResult<T> = ApiResult(200, "ok", data)

fun <T> created(data: T): ApiResult<T> = ApiResult(200, "created", data)

fun <T> updated(data: T): ApiResult<T> = ApiResult(200, "updated", data)

fun okMessage(message: String): ApiResult<String> = ApiResult(200, "ok", message)

/**
 * 分页参数,全系统一:limit 1..200 默认 50,offset >= 0 默认 0。
 */
data class PageParams(
    val limit: Int = 50,
    val offset: Long = 0,
) {
    init {
        if (limit !in 1..200) throw ApiException.badRequest("limit must be between 1 and 200")
        if (offset < 0) throw ApiException.badRequest("offset must not be negative")
    }
}
