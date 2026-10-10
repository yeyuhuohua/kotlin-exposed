package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.common.api.pageParams
import com.atguigu.hrspring.dto.EmployeeCreateRequest
import com.atguigu.hrspring.dto.EmployeeDetailDto
import com.atguigu.hrspring.dto.EmployeeDto
import com.atguigu.hrspring.dto.EmployeeUpdateRequest
import com.atguigu.hrspring.service.EmployeeService
import com.fasterxml.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "employees")
@RestController
class EmployeeController(
    private val employeeService: EmployeeService,
    private val objectMapper: ObjectMapper,
) {
    @Operation(summary = "分页查询员工")
    @GetMapping("/api/employees")
    fun list(
        @RequestParam(name = "limit", required = false) limit: Int?,
        @RequestParam(name = "offset", required = false) offset: Long?,
        @RequestParam(name = "departmentId", required = false) departmentId: String?,
        @RequestParam(name = "jobId", required = false) jobId: String?,
        @RequestParam(name = "q", required = false) q: String?,
    ): ResponseEntity<ApiResult<ApiList<EmployeeDto>>> {
        val unassignedOnly = departmentId == "none"
        val department = if (unassignedOnly) null else departmentId?.toIntOrNull()
        val (data, hit) = employeeService.list(
            pageParams(limit, offset),
            department,
            jobId,
            q,
            unassignedOnly,
        )
        return cachedOk(data, hit)
    }

    @Operation(summary = "新增员工并刷新缓存")
    @PostMapping("/api/employees")
    fun create(
        @RequestBody request: EmployeeCreateRequest,
    ): ResponseEntity<ApiResult<EmployeeDto>> = body(employeeService.create(request), "created")

    @Operation(summary = "按主键查询员工")
    @GetMapping("/api/employees/{id}")
    fun get(
        @PathVariable("id") id: String,
    ): ResponseEntity<ApiResult<EmployeeDto>> {
        val (data, hit) = employeeService.get(employeeId(id))
        return cachedOk(data, hit)
    }

    @Operation(summary = "部分更新员工并刷新缓存")
    @PutMapping("/api/employees/{id}")
    fun update(
        @PathVariable("id") id: String,
        @RequestBody request: EmployeeUpdateRequest,
    ): ResponseEntity<ApiResult<EmployeeDto>> =
        body(employeeService.update(employeeId(id), request), "updated")

    @Operation(summary = "精确部分更新员工（可清空可空字段）")
    @PatchMapping("/api/employees/{id}")
    fun patch(
        @PathVariable("id") id: String,
        @RequestBody patch: JsonNode,
    ): ResponseEntity<ApiResult<EmployeeDto>> =
        body(employeeService.patch(employeeId(id), objectMapper.readTree(patch.toString())), "updated")

    @Operation(summary = "删除员工并清除相关缓存")
    @DeleteMapping("/api/employees/{id}")
    fun delete(
        @PathVariable("id") id: String,
    ): ResponseEntity<ApiResult<String>> {
        employeeService.delete(employeeId(id))
        return body("deleted", "ok")
    }

    @Operation(summary = "员工详情（多表 JOIN）")
    @GetMapping("/api/employees/{id}/details")
    fun details(
        @PathVariable("id") id: String,
    ): ResponseEntity<ApiResult<EmployeeDetailDto>> {
        val (data, hit) = employeeService.getDetail(employeeId(id))
        return cachedOk(data, hit)
    }

    @Operation(summary = "员工详情视图分页")
    @GetMapping("/api/emp-details")
    fun empDetails(
        @RequestParam(name = "limit", required = false) limit: Int?,
        @RequestParam(name = "offset", required = false) offset: Long?,
    ): ResponseEntity<ApiResult<ApiList<EmployeeDetailDto>>> {
        val (data, hit) = employeeService.listDetails(pageParams(limit, offset))
        return cachedOk(data, hit)
    }
}

private fun employeeId(raw: String): Int =
    raw.toIntOrNull() ?: throw ApiException.badRequest("invalid employee id")
