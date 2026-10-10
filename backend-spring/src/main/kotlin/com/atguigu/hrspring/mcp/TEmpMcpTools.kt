package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.TEmpCreateRequest
import com.atguigu.hrspring.dto.TEmpDto
import com.atguigu.hrspring.dto.TEmpUpdateRequest
import com.atguigu.hrspring.service.TEmpService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class TEmpMcpTools(
    private val tEmpService: TEmpService,
) {
    @McpTool(name = "list_t_emp", description = "查询 t_emp 人物示例表，返回全部人物。")
    fun listTEmp(): List<TEmpDto> = tEmpService.list().first

    @McpTool(name = "create_t_emp", description = "新增人物。id 自增，empno 必填；年龄可以省略或为 0，不能为负。")
    fun createTEmp(
        @McpToolParam(description = "工号 empno，必填", required = true) empno: Int,
        @McpToolParam(description = "姓名，最长 20 个字符", required = false) name: String?,
        @McpToolParam(description = "年龄，省略或 0 合法，不能为负", required = false) age: Int?,
        @McpToolParam(description = "所属门派 t_dept.id", required = false) deptId: Int?,
    ): TEmpDto = tEmpService.create(
        TEmpCreateRequest(
            name = name,
            age = age,
            deptId = deptId,
            empno = empno,
        ),
    )

    @McpTool(name = "update_t_emp", description = "按 id 部分更新人物，只改传入的字段。年龄不能为负。")
    fun updateTEmp(
        @McpToolParam(description = "人物主键", required = true) id: Int,
        @McpToolParam(description = "姓名，不传则不改", required = false) name: String?,
        @McpToolParam(description = "年龄，不传则不改，不能为负", required = false) age: Int?,
        @McpToolParam(description = "所属门派 t_dept.id，不传则不改", required = false) deptId: Int?,
        @McpToolParam(description = "工号 empno，不传则不改", required = false) empno: Int?,
    ): TEmpDto = tEmpService.update(
        id,
        TEmpUpdateRequest(
            name = name,
            age = age,
            deptId = deptId,
            empno = empno,
        ),
    )
}
