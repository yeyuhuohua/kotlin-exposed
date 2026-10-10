package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.JobGradeDto
import com.atguigu.hrspring.service.JobGradeService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component

@Component
class JobGradeMcpTools(
    private val jobGradeService: JobGradeService,
) {
    @McpTool(name = "list_job_grades", description = "查询全部薪资等级，按 gradeLevel 升序返回。无参数。")
    fun listJobGrades(): List<JobGradeDto> = jobGradeService.list().first
}
