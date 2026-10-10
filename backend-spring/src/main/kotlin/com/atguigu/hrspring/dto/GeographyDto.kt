package com.atguigu.hrspring.dto
data class CountryDto(
    val countryId: String,
    val countryName: String?,
    val regionId: Int?,
)

data class RegionDto(
    val regionId: Int,
    val regionName: String?,
)
