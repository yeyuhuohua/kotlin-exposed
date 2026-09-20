package com.atguigu.hr.demo.order

import kotlinx.serialization.Serializable

/** order 表示例数据。 */
@Serializable
data class OrderDto(
    val orderId: Int?,
    val orderName: String?,
)
