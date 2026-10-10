package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.JobCreateRequest
import com.atguigu.hrspring.dto.JobDto
import com.atguigu.hrspring.dto.JobUpdateRequest
import com.atguigu.hrspring.service.JobService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "jobs")
@RestController
@RequestMapping("/api/jobs")
class JobController(
    private val jobService: JobService,
) {
    @Operation(summary = "全部岗位")
    @GetMapping
    fun list(): ResponseEntity<ApiResult<List<JobDto>>> {
        val (items, hit) = jobService.list()
        return cachedOk(items, hit)
    }

    @Operation(summary = "新增岗位并刷新缓存")
    @PostMapping
    fun create(@RequestBody request: JobCreateRequest): ResponseEntity<ApiResult<JobDto>> =
        body(jobService.create(request), "created")

    @Operation(summary = "部分更新岗位并刷新缓存")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody patch: JobUpdateRequest,
    ): ResponseEntity<ApiResult<JobDto>> = body(jobService.update(id, patch), "updated")
}
