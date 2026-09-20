package com.atguigu.hr.demo.order

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache

/** 协调示例订单查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object OrderService {
    suspend fun listOrders(): Cached<List<OrderDto>> =
        RedisCache.getOrLoad(OrderCache.ORDERS) { OrderRepository.listOrders() }
}
