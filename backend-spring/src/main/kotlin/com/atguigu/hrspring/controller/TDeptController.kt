package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.TDeptCreateRequest
import com.atguigu.hrspring.dto.TDeptDto
import com.atguigu.hrspring.dto.TDeptUpdateRequest
import com.atguigu.hrspring.service.TDeptService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** t_dept 门派示例表。 */
@Tag(name = "t-dept")
@RestController
class TDeptController(
    private val tDeptService: TDeptService,
) {
    @Operation(summary = "t_dept 门派示例表")
    @GetMapping("/api/t-dept")
    fun list(): ResponseEntity<ApiResult<List<TDeptDto>>> {
        val (data, hit) = tDeptService.list()
        return cachedOk(data, hit)
    }

    @Operation(summary = "新增门派并刷新缓存")
    @PostMapping("/api/t-dept")
    fun create(@RequestBody request: TDeptCreateRequest): ResponseEntity<ApiResult<TDeptDto>> =
        body(tDeptService.create(request), "created")

    @Operation(summary = "部分更新门派并刷新缓存")
    @PutMapping("/api/t-dept/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody patch: TDeptUpdateRequest,
    ): ResponseEntity<ApiResult<TDeptDto>> {
        val deptId = id.toIntOrNull() ?: throw ApiException.badRequest("invalid t_dept id")
        return body(tDeptService.update(deptId, patch), "updated")
    }
}
