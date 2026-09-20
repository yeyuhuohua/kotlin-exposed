package com.atguigu.hr.department

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** departments 表。 */
@Serializable
data class DepartmentDto(
    val departmentId: Int,
    val departmentName: String,
    val managerId: Int?,
    val locationId: Int?,
)

/** PUT /api/departments/{id} */
@Serializable
@JsonSchema.Description("部门部分更新，只提交要改的字段")
data class DepartmentUpdateRequest(
    @JsonSchema.Description("部门名称，不传则不改")
    val departmentName: String? = null,
    @JsonSchema.Description("部门经理 employee_id，不传则不改")
    val managerId: Int? = null,
    @JsonSchema.Description("地点 location_id，不传则不改")
    val locationId: Int? = null,
) {
    fun hasUpdates(): Boolean =
        departmentName != null || managerId != null || locationId != null

    fun contentError(): String? =
        if (departmentName != null && departmentName.isBlank()) "departmentName must not be blank" else null
}

/** POST /api/departments。departmentId 由调用方提供。 */
@Serializable
@JsonSchema.Description("新增部门。departmentId 必填")
data class DepartmentCreateRequest(
    @JsonSchema.Description("部门主键，必填")
    val departmentId: Int,
    @JsonSchema.Description("部门名称，必填")
    val departmentName: String,
    @JsonSchema.Description("部门经理 employee_id")
    val managerId: Int? = null,
    @JsonSchema.Description("地点 location_id")
    val locationId: Int? = null,
)
