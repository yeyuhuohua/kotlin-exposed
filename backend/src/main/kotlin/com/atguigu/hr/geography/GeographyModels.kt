package com.atguigu.hr.geography

import kotlinx.serialization.Serializable

/** countries 表。 */
@Serializable
data class CountryDto(
    val countryId: String,
    val countryName: String?,
    val regionId: Int?,
)

/** regions 表。 */
@Serializable
data class RegionDto(
    val regionId: Int,
    val regionName: String?,
)
