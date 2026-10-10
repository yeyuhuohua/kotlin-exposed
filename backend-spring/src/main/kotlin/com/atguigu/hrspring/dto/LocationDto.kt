package com.atguigu.hrspring.dto
data class LocationDto(
    val locationId: Int,
    val streetAddress: String?,
    val postalCode: String?,
    val city: String,
    val stateProvince: String?,
    val countryId: String?,
)

data class LocationCreateRequest(
    val locationId: Int,
    val streetAddress: String? = null,
    val postalCode: String? = null,
    val city: String,
    val stateProvince: String? = null,
    val countryId: String? = null,
)

data class LocationUpdateRequest(
    val streetAddress: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val stateProvince: String? = null,
    val countryId: String? = null,
) {
    fun hasUpdates(): Boolean =
        streetAddress != null || postalCode != null || city != null ||
            stateProvince != null || countryId != null
}
