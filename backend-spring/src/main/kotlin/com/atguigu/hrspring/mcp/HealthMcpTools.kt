package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.HealthDto
import com.atguigu.hrspring.service.HealthService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component

@Component
class HealthMcpTools(
    private val healthService: HealthService,
) {
    @McpTool(name = "get_health", description = "检查 MySQL 与 Redis 是否可用。status 为 UP 或 DOWN。")
    fun getHealth(): HealthDto = healthService.check().first
}
