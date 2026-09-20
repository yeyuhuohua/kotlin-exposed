package com.atguigu.hr.geography

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.responseExamples
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.geographyRoutes() {
    /** 全部国家。 */
    get("/countries") {
        val cached = GeographyService.listCountries()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "全部国家"
        tag("countries")
        responseExamples(listOf(sampleCountry))
    }

    /** 全部地区。 */
    get("/regions") {
        val cached = GeographyService.listRegions()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "全部地区"
        tag("regions")
        responseExamples(listOf(sampleRegion))
    }
}
