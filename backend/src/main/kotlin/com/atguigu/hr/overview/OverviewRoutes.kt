package com.atguigu.hr.overview

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.responseExamples
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.overviewRoutes() {
    /**
     * 协程并行汇总员工、部门、岗位、地点、地区、t_emp。
     *
     * Tag: system
     * Response: 200 application/json [OverviewDto] 并行查询汇总
     */
    get("/overview") {
        val cached = OverviewService.loadOverview()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "协程并行汇总"
        tag("system")
        responseExamples(sampleOverview)
    }
}
