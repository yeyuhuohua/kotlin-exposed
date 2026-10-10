package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.ApiCallRecordDto
import com.atguigu.hrspring.dto.LoginRecordDto
import com.atguigu.hrspring.service.AuditQueryService
import com.atguigu.hrspring.common.api.ApiList
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class AuditMcpTools(
    private val auditQueryService: AuditQueryService,
) {
    @McpTool(name = "list_login_records", description = "分页查询登录记录。用户名按包含匹配，success 只接受 true 或 false。")
    fun listLoginRecords(
        @McpToolParam(description = "每页条数，默认 50，范围 1 到 200", required = false) limit: Int?,
        @McpToolParam(description = "跳过条数，默认 0", required = false) offset: Long?,
        @McpToolParam(description = "按用户名包含匹配", required = false) username: String?,
        @McpToolParam(description = "true 仅成功，false 仅失败，不传则不过滤", required = false) success: Boolean?,
    ): ApiList<LoginRecordDto> = auditQueryService.listLogins(
        limit = limit,
        offset = offset,
        username = username,
        success = success,
    )

    @McpTool(name = "list_api_calls", description = "分页查询接口调用记录。用户名包含匹配，方法精确匹配，路径按前缀匹配。")
    fun listApiCalls(
        @McpToolParam(description = "每页条数，默认 50，范围 1 到 200", required = false) limit: Int?,
        @McpToolParam(description = "跳过条数，默认 0", required = false) offset: Long?,
        @McpToolParam(description = "按用户名包含匹配", required = false) username: String?,
        @McpToolParam(description = "HTTP 方法，例如 GET", required = false) method: String?,
        @McpToolParam(description = "路径前缀，例如 /api/employees", required = false) path: String?,
    ): ApiList<ApiCallRecordDto> = auditQueryService.listApiCalls(
        limit = limit,
        offset = offset,
        username = username,
        method = method,
        path = path,
    )
}
