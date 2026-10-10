package com.atguigu.hrspring.mcp
import com.atguigu.hrspring.dto.CountryDto
import com.atguigu.hrspring.dto.RegionDto
import com.atguigu.hrspring.service.GeographyService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.stereotype.Component

@Component
class GeographyMcpTools(
    private val geographyService: GeographyService,
) {
    @McpTool(name = "list_countries", description = "查询全部国家，按 countryId 升序返回。无参数。")
    fun listCountries(): List<CountryDto> = geographyService.listCountries().first

    @McpTool(name = "list_regions", description = "查询全部地区，按 regionId 升序返回。无参数。")
    fun listRegions(): List<RegionDto> = geographyService.listRegions().first
}
