package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.ErrorCode
import com.atguigu.hrspring.dto.HealthDto
import com.atguigu.hrspring.service.HealthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 公开健康检查：任一依赖 DOWN 返回 503 与 dependency_unavailable，data 仍带健康详情。 */
@Tag(name = "health")
@RestController
class HealthController(
    private val healthService: HealthService,
) {
    @Operation(summary = "健康检查")
    @GetMapping("/api/health")
    fun health(): ResponseEntity<ApiResult<HealthDto>> {
        val (dto, up) = healthService.check()
        return if (up) {
            ResponseEntity.ok(ApiResult(200, "ok", dto, null))
        } else {
            ResponseEntity.status(503)
                .body(ApiResult(503, "dependency unavailable", dto, ErrorCode.DEPENDENCY_UNAVAILABLE))
        }
    }
}
