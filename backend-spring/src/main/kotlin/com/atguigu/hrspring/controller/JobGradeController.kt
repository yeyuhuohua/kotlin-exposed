package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.JobGradeDto
import com.atguigu.hrspring.service.JobGradeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "job-grades")
@RestController
@RequestMapping("/api/job-grades")
class JobGradeController(
    private val jobGradeService: JobGradeService,
) {
    @Operation(summary = "薪资等级")
    @GetMapping
    fun list(): ResponseEntity<ApiResult<List<JobGradeDto>>> {
        val (items, hit) = jobGradeService.list()
        return cachedOk(items, hit)
    }
}
