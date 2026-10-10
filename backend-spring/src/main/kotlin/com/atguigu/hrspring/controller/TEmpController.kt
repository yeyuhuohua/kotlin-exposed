package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.TEmpCreateRequest
import com.atguigu.hrspring.dto.TEmpDto
import com.atguigu.hrspring.dto.TEmpUpdateRequest
import com.atguigu.hrspring.service.TEmpService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** t_emp 人物示例表。 */
@Tag(name = "t-emp")
@RestController
class TEmpController(
    private val tEmpService: TEmpService,
) {
    @Operation(summary = "t_emp 人物示例表")
    @GetMapping("/api/t-emp")
    fun list(): ResponseEntity<ApiResult<List<TEmpDto>>> {
        val (data, hit) = tEmpService.list()
        return cachedOk(data, hit)
    }

    @Operation(summary = "新增人物并刷新缓存")
    @PostMapping("/api/t-emp")
    fun create(@RequestBody request: TEmpCreateRequest): ResponseEntity<ApiResult<TEmpDto>> =
        body(tEmpService.create(request), "created")

    @Operation(summary = "部分更新人物并刷新缓存")
    @PutMapping("/api/t-emp/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody patch: TEmpUpdateRequest,
    ): ResponseEntity<ApiResult<TEmpDto>> {
        val empId = id.toIntOrNull() ?: throw ApiException.badRequest("invalid t_emp id")
        return body(tEmpService.update(empId, patch), "updated")
    }
}
