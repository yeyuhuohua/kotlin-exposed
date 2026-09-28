package com.atguigu.hr.location

import com.atguigu.hr.common.api.exceedsMaxLength
import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** locations 表。 */
@Serializable
data class LocationDto(
    val locationId: Int,
    val streetAddress: String?,
    val postalCode: String?,
    val city: String,
    val stateProvince: String?,
    val countryId: String?,
)

/** PUT /api/locations/{id} */
@Serializable
@JsonSchema.Description("地点部分更新，只提交要改的字段")
data class LocationUpdateRequest(
    @JsonSchema.Description("街道地址，不传则不改")
    val streetAddress: String? = null,
    @JsonSchema.Description("邮编，不传则不改")
    val postalCode: String? = null,
    @JsonSchema.Description("城市，不传则不改")
    val city: String? = null,
    @JsonSchema.Description("州/省，不传则不改")
    val stateProvince: String? = null,
    @JsonSchema.Description("国家编码，例如 US，不传则不改")
    val countryId: String? = null,
) {
    fun hasUpdates(): Boolean =
        streetAddress != null || postalCode != null || city != null ||
            stateProvince != null || countryId != null

    fun contentError(): String? {
        if (city != null && city.isBlank()) return "city must not be blank"
        if (streetAddress != null && streetAddress.exceedsMaxLength(40)) {
            return "streetAddress must be at most 40 characters"
        }
        if (postalCode != null && postalCode.exceedsMaxLength(12)) return "postalCode must be at most 12 characters"
        if (city != null && city.exceedsMaxLength(30)) return "city must be at most 30 characters"
        if (stateProvince != null && stateProvince.exceedsMaxLength(25)) {
            return "stateProvince must be at most 25 characters"
        }
        if (countryId != null && countryId.exceedsMaxLength(2)) return "countryId must be at most 2 characters"
        return null
    }
}

/** POST /api/locations。locationId 由调用方提供。 */
@Serializable
@JsonSchema.Description("新增地点。locationId 必填")
data class LocationCreateRequest(
    @JsonSchema.Description("地点主键，必填")
    val locationId: Int,
    @JsonSchema.Description("街道地址")
    val streetAddress: String? = null,
    @JsonSchema.Description("邮编")
    val postalCode: String? = null,
    @JsonSchema.Description("城市，必填")
    val city: String,
    @JsonSchema.Description("州/省")
    val stateProvince: String? = null,
    @JsonSchema.Description("国家编码，例如 US")
    val countryId: String? = null,
) {
    fun contentError(): String? {
        if (city.isBlank()) return "city is required"
        if (streetAddress != null && streetAddress.exceedsMaxLength(40)) {
            return "streetAddress must be at most 40 characters"
        }
        if (postalCode != null && postalCode.exceedsMaxLength(12)) return "postalCode must be at most 12 characters"
        if (city.exceedsMaxLength(30)) return "city must be at most 30 characters"
        if (stateProvince != null && stateProvince.exceedsMaxLength(25)) {
            return "stateProvince must be at most 25 characters"
        }
        if (countryId != null && countryId.exceedsMaxLength(2)) return "countryId must be at most 2 characters"
        return null
    }
}
