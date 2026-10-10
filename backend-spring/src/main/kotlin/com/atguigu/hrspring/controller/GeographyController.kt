package com.atguigu.hrspring.controller
import com.atguigu.hrspring.common.api.ApiResult
import com.atguigu.hrspring.common.api.cachedOk
import com.atguigu.hrspring.dto.CountryDto
import com.atguigu.hrspring.dto.RegionDto
import com.atguigu.hrspring.service.GeographyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class GeographyController(
    private val geographyService: GeographyService,
) {
    @Tag(name = "countries")
    @Operation(summary = "全部国家")
    @GetMapping("/countries")
    fun countries(): ResponseEntity<ApiResult<List<CountryDto>>> {
        val (items, hit) = geographyService.listCountries()
        return cachedOk(items, hit)
    }

    @Tag(name = "regions")
    @Operation(summary = "全部地区")
    @GetMapping("/regions")
    fun regions(): ResponseEntity<ApiResult<List<RegionDto>>> {
        val (items, hit) = geographyService.listRegions()
        return cachedOk(items, hit)
    }
}
