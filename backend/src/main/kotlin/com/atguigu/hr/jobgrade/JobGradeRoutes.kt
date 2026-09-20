package com.atguigu.hr.jobgrade

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.responseExamples
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.jobGradeRoutes() {
    /** 薪资等级。 */
    get("/job-grades") {
        val cached = JobGradeService.listJobGrades()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "薪资等级"
        tag("job-grades")
        responseExamples(listOf(sampleJobGrade))
    }
}
