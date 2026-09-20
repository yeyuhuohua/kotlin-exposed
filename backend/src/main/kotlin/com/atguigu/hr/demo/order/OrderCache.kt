package com.atguigu.hr.demo.order

/** 集中定义示例订单缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object OrderCache {
    const val ORDERS = "hr:orders"
}
