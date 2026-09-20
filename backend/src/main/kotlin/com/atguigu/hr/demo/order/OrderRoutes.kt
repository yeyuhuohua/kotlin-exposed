package com.atguigu.hr.demo.order

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.responseExamples
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.orderRoutes() {
    /** order 表示例数据。 */
    get("/orders") {
        val cached = OrderService.listOrders()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "order 表示例数据"
        tag("orders")
        responseExamples(listOf(sampleOrder))
    }
}
