package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.OrderDto
import com.atguigu.hrspring.service.OrderService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** order 表示例数据。 */
@Tag(name = "orders")
@RestController
class OrderController(
    private val orderService: OrderService,
) {
    @Operation(summary = "order 表示例数据")
    @GetMapping("/api/orders")
    fun list(): ResponseEntity<ApiResult<List<OrderDto>>> {
        val (data, hit) = orderService.list()
        return cachedOk(data, hit)
    }
}
