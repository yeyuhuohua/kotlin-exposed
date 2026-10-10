package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.LocationCreateRequest
import com.atguigu.hrspring.dto.LocationDto
import com.atguigu.hrspring.dto.LocationUpdateRequest
import com.atguigu.hrspring.service.LocationService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class LocationMcpTools(
    private val locationService: LocationService,
) {
    @McpTool(name = "list_locations", description = "查询全部办公地点，按 locationId 升序返回。无参数。")
    fun listLocations(): List<LocationDto> = locationService.list().first

    @McpTool(
        name = "create_location",
        description = "新增办公地点。locationId、city 必填，city 最长 30。streetAddress 最长 40，postalCode 最长 12，stateProvince 最长 25，countryId 最长 2，均可选。",
    )
    fun createLocation(
        @McpToolParam(description = "新地点。locationId、city 必填；streetAddress、postalCode、stateProvince、countryId 可选")
        request: LocationCreateRequest,
    ): LocationDto = locationService.create(request)

    @McpTool(
        name = "update_location",
        description = "按 locationId 部分更新办公地点。request 里只传要改的字段，不传的保持原值。",
    )
    fun updateLocation(
        @McpToolParam(description = "地点主键 location_id，例如 1700") locationId: Int,
        @McpToolParam(description = "要修改的字段，至少一项：streetAddress、postalCode、city、stateProvince、countryId")
        request: LocationUpdateRequest,
    ): LocationDto = locationService.update(locationId, request)
}
