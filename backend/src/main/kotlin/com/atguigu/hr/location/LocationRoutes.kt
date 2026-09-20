package com.atguigu.hr.location

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondFail
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.requestExample
import com.atguigu.hr.docs.responseExamples
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.locationRoutes() {
    /**
     * 全部地点。
     *
     * Tag: locations
     * Response: 200 application/json [LocationDto] 地点列表
     */
    get("/locations") {
        val cached = LocationService.listLocations()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "全部地点"
        tag("locations")
        responseExamples(listOf(sampleLocation))
    }

    /**
     * 新增地点并刷新缓存。locationId 由调用方提供。
     *
     * Tag: locations
     * Body: application/json [LocationCreateRequest] locationId、city 必填
     * Response: 200 application/json [LocationDto] 新建地点
     * Response: 400 城市为空
     * Response: 409 主键冲突或外键失败
     */
    post("/locations") {
        val body = call.receive<LocationCreateRequest>()
        if (body.city.isBlank()) {
            return@post call.respondFail(HttpStatusCode.BadRequest, "city is required")
        }
        val created = LocationService.createLocation(body)
        call.respondOk(created, message = "created")
    }.describe {
        summary = "新增地点并刷新缓存"
        tag("locations")
        requestExample(sampleLocationCreate, description = "locationId、city 必填")
        responseExamples(
            sampleLocation.copy(
                locationId = 3300,
                streetAddress = "1 Kotlin Rd",
                postalCode = "00000",
                city = "Hangzhou",
                stateProvince = "Zhejiang",
            ),
            message = "created",
            okDescription = "创建成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "city is required",
                HttpStatusCode.Conflict to "create failed",
            ),
        )
    }

    /**
     * 部分更新地点并刷新缓存。
     *
     * Tag: locations
     * Path: [Int] id 地点主键 location_id，例如 1700
     * Body: application/json [LocationUpdateRequest] 只提交要改的字段
     * Response: 200 application/json [LocationDto] 更新后的地点
     * Response: 400 无更新字段或 ID 无效
     * Response: 404 地点不存在
     * Response: 409 外键等约束冲突
     */
    put("/locations/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid location id")
        val patch = call.receive<LocationUpdateRequest>()
        if (!patch.hasUpdates()) {
            return@put call.respondFail(HttpStatusCode.BadRequest, "no fields to update")
        }
        patch.contentError()?.let { return@put call.respondFail(HttpStatusCode.BadRequest, it) }
        val updated = LocationService.updateLocation(id, patch)
            ?: return@put call.respondFail(HttpStatusCode.NotFound, "location not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "部分更新地点并刷新缓存"
        tag("locations")
        parameters {
            path("id") { description = "地点主键 location_id，例如 1700" }
        }
        requestExample(sampleLocationPatch)
        responseExamples(
            sampleLocation,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "no fields to update",
                HttpStatusCode.NotFound to "location not found",
                HttpStatusCode.Conflict to "update failed",
            ),
        )
    }
}
