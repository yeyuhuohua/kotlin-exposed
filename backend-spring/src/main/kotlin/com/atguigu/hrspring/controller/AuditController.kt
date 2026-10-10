package com.atguigu.hrspring.controller
import com.atguigu.hrspring.dto.ApiCallRecordDto
import com.atguigu.hrspring.dto.LoginRecordDto
import com.atguigu.hrspring.service.AuditQueryService
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 审计日志查询。两张表只读，不写 X-Cache。 */
@Tag(name = "audit")
@RestController
class AuditController(
    private val auditQueryService: AuditQueryService,
) {
    @Operation(summary = "分页查询登录记录（仅 ADMIN）")
    @GetMapping("/api/audit/logins")
    fun logins(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Long?,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) success: String?,
    ): ResponseEntity<ApiResult<ApiList<LoginRecordDto>>> = body(
        auditQueryService.listLogins(
            limit = limit,
            offset = offset,
            username = username,
            success = success?.toBooleanStrictOrNull(),
        ),
        "ok",
    )

    @Operation(summary = "分页查询接口调用记录（仅 ADMIN）")
    @GetMapping("/api/audit/api-calls")
    fun apiCalls(
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) offset: Long?,
        @RequestParam(required = false) username: String?,
        @RequestParam(required = false) method: String?,
        @RequestParam(required = false) path: String?,
    ): ResponseEntity<ApiResult<ApiList<ApiCallRecordDto>>> = body(
        auditQueryService.listApiCalls(
            limit = limit,
            offset = offset,
            username = username,
            method = method,
            path = path,
        ),
        "ok",
    )
}
