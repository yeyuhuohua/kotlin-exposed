package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.JobHistoryDto
import com.atguigu.hrspring.service.JobHistoryService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext
import org.springframework.stereotype.Component

@Component
class JobHistoryMcpTools(
    private val jobHistoryService: JobHistoryService,
) {
    /** 查询全部任职历史，按员工编号和开始日期升序。 */
    @McpTool(name = "list_job_history", description = "查询全部任职历史，按员工编号和开始日期升序。")
    fun listJobHistory(context: McpSyncRequestContext): List<JobHistoryDto> = jobHistoryService.list()
}
