package com.atguigu.hrspring.config

import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.ErrorCode
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSourceResolvable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.Errors
import org.springframework.validation.method.MethodValidationException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
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
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
        MethodValidationException::class,
        ConstraintViolationException::class,
    )
    fun validation(e: Exception): ResponseEntity<ApiResult<Nothing>> {
        log.debug("validation failed: {}", e.message)
        return ResponseEntity.status(400)
            .body(ApiResult(400, validationMessage(e), null, ErrorCode.VALIDATION_FAILED))
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResult<Nothing>> =
        ResponseEntity.status(405)
            .headers(e.headers)
            .body(ApiResult(405, "method not allowed", null, "method_not_allowed"))

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun unsupportedMedia(e: HttpMediaTypeNotSupportedException): ResponseEntity<ApiResult<Nothing>> =
        ResponseEntity.status(415)
            .headers(e.headers)
            .body(ApiResult(415, "unsupported media type", null, ErrorCode.UNSUPPORTED_MEDIA_TYPE))

    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun notAcceptable(e: HttpMediaTypeNotAcceptableException): ResponseEntity<ApiResult<Nothing>> =
        ResponseEntity.status(406)
            .body(ApiResult(406, "not acceptable", null, "not_acceptable"))

    private fun validationMessage(e: Exception): String {
        val messages = when (e) {
            is MethodArgumentNotValidException -> e.bindingResult.allErrors.mapNotNull { it.defaultMessage }
            is HandlerMethodValidationException -> e.allErrors.mapNotNull { it.defaultMessage }
            is MethodValidationException -> methodMessages(e)
            is ConstraintViolationException -> e.constraintViolations.map { it.message }
            else -> emptyList()
        }
        return messages.firstOrNull { it.isNotBlank() } ?: "invalid request"
    }

    private fun methodMessages(e: MethodValidationException): List<String> =
        e.parameterValidationResults.flatMap { result ->
            if (result is Errors) {
                result.allErrors.mapNotNull(MessageSourceResolvable::getDefaultMessage)
            } else {
                result.resolvableErrors.mapNotNull(MessageSourceResolvable::getDefaultMessage)
            }
        }
}
