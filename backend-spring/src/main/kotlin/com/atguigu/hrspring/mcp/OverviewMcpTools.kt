package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.OverviewDto
import com.atguigu.hrspring.service.OverviewService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component

@Component
class OverviewMcpTools(
    private val overviewService: OverviewService,
) {
    @McpTool(
        name = "get_overview",
        description = "汇总员工样本、部门人数、薪资，以及部门、岗位、地点、区域和示例人员数量。",
    )
    fun getOverview(): OverviewDto = overviewService.load().first
}
