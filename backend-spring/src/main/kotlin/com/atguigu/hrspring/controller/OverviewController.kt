package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.OverviewDto
import com.atguigu.hrspring.service.OverviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "system")
@RestController
class OverviewController(
    private val overviewService: OverviewService,
) {
    @Operation(summary = "协程并行汇总")
    @GetMapping("/api/overview")
    fun overview(): ResponseEntity<ApiResult<OverviewDto>> {
        val (data, hit) = overviewService.load()
        return cachedOk(data, hit)
    }
}
