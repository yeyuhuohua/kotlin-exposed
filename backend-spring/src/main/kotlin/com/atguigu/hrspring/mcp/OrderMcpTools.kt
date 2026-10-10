package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.OrderDto
import com.atguigu.hrspring.service.OrderService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component

@Component
class OrderMcpTools(
    private val orderService: OrderService,
) {
    @McpTool(name = "list_orders", description = "查询 order 表示例订单，返回全部订单号与名称。")
    fun listOrders(): List<OrderDto> = orderService.list().first
}
