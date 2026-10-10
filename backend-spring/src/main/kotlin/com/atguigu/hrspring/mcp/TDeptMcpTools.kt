package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.TDeptCreateRequest
import com.atguigu.hrspring.dto.TDeptDto
import com.atguigu.hrspring.dto.TDeptUpdateRequest
import com.atguigu.hrspring.service.TDeptService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class TDeptMcpTools(
    private val tDeptService: TDeptService,
) {
    @McpTool(name = "list_t_dept", description = "查询 t_dept 门派示例表，返回全部门派。")
    fun listTDept(): List<TDeptDto> = tDeptService.list().first

    @McpTool(name = "create_t_dept", description = "新增门派。id 自增，至少填写 deptName 或 address。")
    fun createTDept(
        @McpToolParam(description = "门派名，最长 30 个字符", required = false) deptName: String?,
        @McpToolParam(description = "所在地，最长 30 个字符", required = false) address: String?,
    ): TDeptDto = tDeptService.create(
        TDeptCreateRequest(
            deptName = deptName,
            address = address,
        ),
    )

    @McpTool(name = "update_t_dept", description = "按 id 部分更新门派，只改传入的字段。")
    fun updateTDept(
        @McpToolParam(description = "门派主键", required = true) id: Int,
        @McpToolParam(description = "门派名，不传则不改", required = false) deptName: String?,
        @McpToolParam(description = "所在地，不传则不改", required = false) address: String?,
    ): TDeptDto = tDeptService.update(
        id,
        TDeptUpdateRequest(
            deptName = deptName,
            address = address,
        ),
    )
}
