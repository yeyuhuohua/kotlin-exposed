package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.validation.CodePointSize
import com.atguigu.hrspring.common.validation.NotBlankIfPresent
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank

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
    @field:CodePointSize(max = 40, message = "streetAddress must be at most 40 characters")
    val streetAddress: String? = null,
    @field:CodePointSize(max = 12, message = "postalCode must be at most 12 characters")
    val postalCode: String? = null,
    @field:NotBlank(message = "city is required")
    @field:CodePointSize(max = 30, message = "city must be at most 30 characters")
    val city: String,
    @field:CodePointSize(max = 25, message = "stateProvince must be at most 25 characters")
    val stateProvince: String? = null,
    @field:CodePointSize(max = 2, message = "countryId must be at most 2 characters")
    val countryId: String? = null,
)

data class LocationUpdateRequest(
    @field:CodePointSize(max = 40, message = "streetAddress must be at most 40 characters")
    val streetAddress: String? = null,
    @field:CodePointSize(max = 12, message = "postalCode must be at most 12 characters")
    val postalCode: String? = null,
    @field:NotBlankIfPresent(message = "city must not be blank")
    @field:CodePointSize(max = 30, message = "city must be at most 30 characters")
    val city: String? = null,
    @field:CodePointSize(max = 25, message = "stateProvince must be at most 25 characters")
    val stateProvince: String? = null,
    @field:CodePointSize(max = 2, message = "countryId must be at most 2 characters")
    val countryId: String? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean =
        streetAddress != null || postalCode != null || city != null ||
            stateProvince != null || countryId != null
}
