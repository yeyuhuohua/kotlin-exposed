package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.pageParams
import com.atguigu.hrspring.dto.EmployeeCreateRequest
import com.atguigu.hrspring.dto.EmployeeDetailDto
import com.atguigu.hrspring.dto.EmployeeDto
import com.atguigu.hrspring.dto.EmployeeUpdateRequest
import com.atguigu.hrspring.service.EmployeeService
import com.fasterxml.jackson.databind.ObjectMapper
import tools.jackson.databind.JsonNode
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class EmployeeMcpTools(
    private val employeeService: EmployeeService,
    private val objectMapper: ObjectMapper,
) {
    @McpTool(
        name = "list_employees",
        description = "分页查询员工。可按部门、岗位、姓名或邮箱筛选；unassignedOnly 为 true 时只看未分配部门。",
    )
    fun listEmployees(
        @McpToolParam(description = "每页条数，默认 50，最大 200", required = false) limit: Int?,
        @McpToolParam(description = "跳过条数，默认 0", required = false) offset: Long?,
        @McpToolParam(description = "部门 ID，不传则不按部门过滤", required = false) departmentId: Int?,
        @McpToolParam(description = "岗位编码，精确匹配，例如 IT_PROG", required = false) jobId: String?,
        @McpToolParam(description = "姓名或邮箱模糊搜索", required = false) q: String?,
        @McpToolParam(description = "为 true 时只查未分配部门的员工，对应 HTTP 的 departmentId=none", required = false)
        unassignedOnly: Boolean?,
    ): ApiList<EmployeeDto> = employeeService.list(
        pageParams(limit, offset),
        departmentId,
        jobId,
        q,
        unassignedOnly == true,
    ).first

    @McpTool(name = "get_employee", description = "按主键查询员工。")
    fun getEmployee(
        @McpToolParam(description = "员工主键 employee_id", required = true) id: Int,
    ): EmployeeDto = employeeService.get(id).first

    @McpTool(
        name = "create_employee",
        description = "新增员工并刷新缓存。employeeId、lastName、email、hireDate、jobId 必填。",
    )
    fun createEmployee(
        @McpToolParam(description = "员工主键", required = true) employeeId: Int,
        @McpToolParam(description = "姓", required = true) lastName: String,
        @McpToolParam(description = "邮箱账号，库内唯一，不含域名", required = true) email: String,
        @McpToolParam(description = "入职日期 yyyy-MM-dd", required = true) hireDate: String,
        @McpToolParam(description = "岗位编码，例如 IT_PROG", required = true) jobId: String,
        @McpToolParam(description = "名", required = false) firstName: String?,
        @McpToolParam(description = "电话", required = false) phoneNumber: String?,
        @McpToolParam(description = "月薪，非负", required = false) salary: Double?,
        @McpToolParam(description = "提成比例，0 到 1", required = false) commissionPct: Double?,
        @McpToolParam(description = "直属经理 employee_id", required = false) managerId: Int?,
        @McpToolParam(description = "所属部门 ID", required = false) departmentId: Int?,
    ): EmployeeDto = employeeService.create(
        EmployeeCreateRequest().apply {
            this.employeeId = employeeId
            this.firstName = firstName
            this.lastName = lastName
            this.email = email
            this.phoneNumber = phoneNumber
            this.hireDate = hireDate
            this.jobId = jobId
            this.salary = salary
            this.commissionPct = commissionPct
            this.managerId = managerId
            this.departmentId = departmentId
        },
    )

    @McpTool(name = "update_employee", description = "部分更新员工的薪资、部门、岗位或电话。未传的字段不改，不能清空。")
    fun updateEmployee(
        @McpToolParam(description = "员工主键 employee_id", required = true) id: Int,
        @McpToolParam(description = "月薪，非负；不传则不改", required = false) salary: Double?,
        @McpToolParam(description = "所属部门 ID；不传则不改", required = false) departmentId: Int?,
        @McpToolParam(description = "岗位编码；不传则不改", required = false) jobId: String?,
        @McpToolParam(description = "电话；不传则不改", required = false) phoneNumber: String?,
    ): EmployeeDto = employeeService.update(
        id,
        EmployeeUpdateRequest().apply {
            this.salary = salary
            this.departmentId = departmentId
            this.jobId = jobId
            this.phoneNumber = phoneNumber
        },
    )

    @McpTool(
        name = "patch_employee",
        description = "精确部分更新员工。只改 patch 里出现的字段；值为 null 表示清空可空列。允许 firstName、salary、commissionPct、departmentId、managerId、phoneNumber、jobId。jobId 不能为 null。",
    )
    fun patchEmployee(
        @McpToolParam(description = "员工主键 employee_id", required = true) id: Int,
        @McpToolParam(
            description = "要修改的字段对象。显式 null 清空可空列，缺省字段不改",
            required = true,
        ) patch: JsonNode,
    ): EmployeeDto = employeeService.patch(id, objectMapper.readTree(patch.toString()))

    @McpTool(name = "delete_employee", description = "删除员工并清除相关缓存。被任职历史等外键引用时会失败。")
    fun deleteEmployee(
        @McpToolParam(description = "员工主键 employee_id", required = true) id: Int,
    ): String {
        employeeService.delete(id)
        return "deleted"
    }

    @McpTool(name = "get_employee_details", description = "查询员工详情，包含岗位、部门、地点、国家和区域。")
    fun getEmployeeDetails(
        @McpToolParam(description = "员工主键 employee_id", required = true) id: Int,
    ): EmployeeDetailDto = employeeService.getDetail(id).first

    @McpTool(name = "list_emp_details", description = "分页查询员工详情视图 emp_details_view。视图没有邮箱、电话和入职日期。")
    fun listEmpDetails(
        @McpToolParam(description = "每页条数，默认 50，最大 200", required = false) limit: Int?,
        @McpToolParam(description = "跳过条数，默认 0", required = false) offset: Long?,
    ): ApiList<EmployeeDetailDto> = employeeService.listDetails(pageParams(limit, offset)).first
}
