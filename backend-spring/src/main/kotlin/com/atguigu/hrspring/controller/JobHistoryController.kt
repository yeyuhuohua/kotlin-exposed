package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.JobHistoryDto
import com.atguigu.hrspring.service.JobHistoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "job-history")
@RestController
@RequestMapping("/api/job-history")
class JobHistoryController(
    private val jobHistoryService: JobHistoryService,
) {
    @Operation(summary = "任职历史")
    @GetMapping
    fun list(): ResponseEntity<ApiResult<List<JobHistoryDto>>> {
        val (rows, hit) = jobHistoryService.list()
        return cachedOk(rows, hit)
    }
}
