package com.atguigu.hr.demo.order

import com.atguigu.hr.config.DatabaseFactory.dbQuery
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.selectAll

/** 示例订单持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object OrderRepository {
    suspend fun listOrders(): List<OrderDto> = dbQuery {
        Orders.selectAll()
            .map { it.toOrder() }
            .toList()
    }
}

private fun ResultRow.toOrder() = OrderDto(
    orderId = this[Orders.orderId],
    orderName = this[Orders.orderName],
)
