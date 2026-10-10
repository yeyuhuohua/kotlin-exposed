package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.DepartmentCreateRequest
import com.atguigu.hrspring.dto.DepartmentDto
import com.atguigu.hrspring.dto.DepartmentUpdateRequest
import com.atguigu.hrspring.service.DepartmentService
import com.atguigu.hrspring.dto.EmployeeDto
import com.atguigu.hrspring.service.EmployeeService
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

@Tag(name = "departments")
@RestController
@RequestMapping("/api/departments")
class DepartmentController(
    private val departmentService: DepartmentService,
    private val employeeService: EmployeeService,
) {
    @Operation(summary = "全部部门")
    @GetMapping
    fun list(): ResponseEntity<ApiResult<List<DepartmentDto>>> {
        val (items, hit) = departmentService.list()
        return cachedOk(items, hit)
    }

    @Operation(summary = "新增部门并刷新缓存")
    @PostMapping
    fun create(@RequestBody request: DepartmentCreateRequest): ResponseEntity<ApiResult<DepartmentDto>> =
        body(departmentService.create(request), "created")

    @Operation(summary = "按主键查询部门")
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<ApiResult<DepartmentDto>> {
        val (dto, hit) = departmentService.find(departmentId(id))
        return cachedOk(dto, hit)
    }

    @Operation(summary = "部分更新部门并刷新缓存")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody patch: DepartmentUpdateRequest,
    ): ResponseEntity<ApiResult<DepartmentDto>> =
        body(departmentService.update(departmentId(id), patch), "updated")

    @Operation(summary = "某部门下的员工")
    @GetMapping("/{id}/employees")
    fun employees(@PathVariable id: String): ResponseEntity<ApiResult<List<EmployeeDto>>> {
        val (items, hit) = employeeService.listByDepartment(departmentId(id))
        return cachedOk(items, hit)
    }

    private fun departmentId(raw: String): Int =
        raw.toIntOrNull() ?: throw ApiException.badRequest("invalid department id")
}
