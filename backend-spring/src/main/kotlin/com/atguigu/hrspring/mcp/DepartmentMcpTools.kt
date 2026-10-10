package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.DepartmentCreateRequest
import com.atguigu.hrspring.dto.DepartmentDto
import com.atguigu.hrspring.dto.DepartmentUpdateRequest
import com.atguigu.hrspring.service.DepartmentService
import com.atguigu.hrspring.dto.EmployeeDto
import com.atguigu.hrspring.service.EmployeeService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class DepartmentMcpTools(
    private val departmentService: DepartmentService,
    private val employeeService: EmployeeService,
) {
    @McpTool(name = "list_departments", description = "查询全部部门，按 departmentId 升序返回。无参数。")
    fun listDepartments(): List<DepartmentDto> = departmentService.list().first

    @McpTool(name = "get_department", description = "按 departmentId 查询单个部门。部门不存在时报 department not found。")
    fun getDepartment(
        @McpToolParam(description = "部门主键 department_id，例如 90") departmentId: Int,
    ): DepartmentDto = departmentService.find(departmentId).first

    @McpTool(
        name = "create_department",
        description = "新增部门。departmentId、departmentName 必填，departmentName 最长 30。managerId、locationId 可选。",
    )
    fun createDepartment(
        @McpToolParam(description = "新部门。departmentId、departmentName 必填；managerId、locationId 可选")
        request: DepartmentCreateRequest,
    ): DepartmentDto = departmentService.create(request)

    @McpTool(
        name = "update_department",
        description = "按 departmentId 部分更新部门。request 里只传要改的字段（departmentName、managerId、locationId），不传的保持原值。",
    )
    fun updateDepartment(
        @McpToolParam(description = "部门主键 department_id") departmentId: Int,
        @McpToolParam(description = "要修改的字段，至少一项；未出现的字段不改") request: DepartmentUpdateRequest,
    ): DepartmentDto = departmentService.update(departmentId, request)

    @McpTool(
        name = "list_department_employees",
        description = "查询指定部门下的员工。departmentId 为部门主键。部门不存在时仍返回该部门 id 下的员工列表（通常为空），不报 404。",
    )
    fun listDepartmentEmployees(
        @McpToolParam(description = "部门主键 department_id") departmentId: Int,
    ): List<EmployeeDto> = employeeService.listByDepartment(departmentId).first
}
