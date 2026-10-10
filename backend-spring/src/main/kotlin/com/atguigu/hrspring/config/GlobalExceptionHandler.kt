package com.atguigu.hrspring.config

import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 全局异常兜底:任何失败响应都是统一信封,HTTP 状态与 code 一致;
 * 数据库细节只进日志,不回显给调用方。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun api(e: ApiException): ResponseEntity<ApiResult<Nothing>> {
        val builder = ResponseEntity.status(e.status)
        if (e.status == 401) builder.header("WWW-Authenticate", "Bearer realm=\"hr-api\"")
        return builder.body(ApiResult(e.status, e.message, null, e.resolvedError))
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        IllegalArgumentException::class,
    )
    fun badRequest(e: Exception): ResponseEntity<ApiResult<Nothing>> {
        log.debug("bad request: {}", e.message)
        return ResponseEntity.status(400)
            .body(ApiResult(400, "invalid request", null, ErrorCode.VALIDATION_FAILED))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun conflict(e: DataIntegrityViolationException): ResponseEntity<ApiResult<Nothing>> {
        log.warn("constraint conflict: {}", e.message)
        return ResponseEntity.status(409)
            .body(ApiResult(409, "resource conflict", null, ErrorCode.CONFLICT))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun notFound(e: NoResourceFoundException): ResponseEntity<ApiResult<Nothing>> {
        return ResponseEntity.status(404)
            .body(ApiResult(404, "not found", null, ErrorCode.NOT_FOUND))
    }

    @ExceptionHandler(Exception::class)
    fun internal(e: Exception): ResponseEntity<ApiResult<Nothing>> {
        log.error("unhandled error", e)
        return ResponseEntity.status(500)
            .body(ApiResult(500, "internal error", null, ErrorCode.INTERNAL_ERROR))
    }
}
