package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.body
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.LocationCreateRequest
import com.atguigu.hrspring.dto.LocationDto
import com.atguigu.hrspring.dto.LocationUpdateRequest
import com.atguigu.hrspring.service.LocationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "locations")
@RestController
@RequestMapping("/api/locations")
class LocationController(
    private val locationService: LocationService,
) {
    @Operation(summary = "全部地点")
    @GetMapping
    fun list(): ResponseEntity<ApiResult<List<LocationDto>>> {
        val (items, hit) = locationService.list()
        return cachedOk(items, hit)
    }

    @Operation(summary = "新增地点并刷新缓存")
    @PostMapping
    fun create(@RequestBody request: LocationCreateRequest): ResponseEntity<ApiResult<LocationDto>> =
        body(locationService.create(request), "created")

    @Operation(summary = "部分更新地点并刷新缓存")
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody patch: LocationUpdateRequest,
    ): ResponseEntity<ApiResult<LocationDto>> =
        body(locationService.update(locationId(id), patch), "updated")

    private fun locationId(raw: String): Int =
        raw.toIntOrNull() ?: throw ApiException.badRequest("invalid location id")
}
