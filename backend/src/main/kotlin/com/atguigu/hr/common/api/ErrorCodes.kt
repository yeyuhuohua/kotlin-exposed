package com.atguigu.hr.common.api

import io.ktor.http.HttpStatusCode

/**
 * 稳定的机器可读错误码。客户端按它选择提示文案，不再依赖 message 的具体措辞；
 * message 只作为人读的补充，可以随时改写而不影响前端。
 */
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

    /** 未显式指定错误码时按 HTTP 状态归类，保证每个失败响应都有稳定的 error。 */
    fun forStatus(status: HttpStatusCode): String = when (status.value) {
        400 -> VALIDATION_FAILED
        401 -> UNAUTHORIZED
        403 -> FORBIDDEN
        404 -> NOT_FOUND
        409 -> CONFLICT
        415 -> UNSUPPORTED_MEDIA_TYPE
        429 -> RATE_LIMITED
        500 -> INTERNAL_ERROR
        503 -> DEPENDENCY_UNAVAILABLE
        else -> "error"
    }
}
