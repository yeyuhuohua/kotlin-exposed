package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.JobCreateRequest
import com.atguigu.hrspring.dto.JobDto
import com.atguigu.hrspring.dto.JobUpdateRequest
import com.atguigu.hrspring.service.JobService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class JobMcpTools(
    private val jobService: JobService,
) {
    @McpTool(name = "list_jobs", description = "查询全部岗位，按 jobId 升序返回。无参数。")
    fun listJobs(): List<JobDto> = jobService.list().first

    @McpTool(
        name = "create_job",
        description = "新增岗位。jobId 为主键编码（最长 10，不能是 . 或 ..），jobTitle 为名称（最长 35），两者必填。minSalary、maxSalary 可选，必须非负，且最低不超过最高。",
    )
    fun createJob(
        @McpToolParam(description = "新岗位。jobId、jobTitle 必填；minSalary、maxSalary 可选") request: JobCreateRequest,
    ): JobDto = jobService.create(request)

    @McpTool(
        name = "update_job",
        description = "按 jobId 部分更新岗位。request 里只传要改的字段（jobTitle、minSalary、maxSalary），不传的保持原值。薪资会与库中另一端合并，最低不得超过最高。",
    )
    fun updateJob(
        @McpToolParam(description = "岗位编码 job_id，例如 AD_PRES") jobId: String,
        @McpToolParam(description = "要修改的字段，至少一项；未出现的字段不改") request: JobUpdateRequest,
    ): JobDto = jobService.update(jobId, request)
}
