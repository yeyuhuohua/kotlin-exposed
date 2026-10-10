package com.atguigu.hrspring.dto
data class DepartmentDto(
    val departmentId: Int,
    val departmentName: String,
    val managerId: Int?,
    val locationId: Int?,
)

data class DepartmentCreateRequest(
    val departmentId: Int,
    val departmentName: String,
    val managerId: Int? = null,
    val locationId: Int? = null,
)

data class DepartmentUpdateRequest(
    val departmentName: String? = null,
    val managerId: Int? = null,
    val locationId: Int? = null,
) {
    fun hasUpdates(): Boolean =
        departmentName != null || managerId != null || locationId != null
}
