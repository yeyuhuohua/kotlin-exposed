package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.validation.CodePointSize
import com.atguigu.hrspring.common.validation.NotBlankIfPresent
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank

data class DepartmentDto(
    val departmentId: Int,
    val departmentName: String,
    val managerId: Int?,
    val locationId: Int?,
)

data class DepartmentCreateRequest(
    val departmentId: Int,
    @field:NotBlank(message = "departmentName is required")
    @field:CodePointSize(max = 30, message = "departmentName must be at most 30 characters")
    val departmentName: String,
    val managerId: Int? = null,
    val locationId: Int? = null,
)

data class DepartmentUpdateRequest(
    @field:NotBlankIfPresent(message = "departmentName must not be blank")
    @field:CodePointSize(max = 30, message = "departmentName must be at most 30 characters")
    val departmentName: String? = null,
    val managerId: Int? = null,
    val locationId: Int? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean =
        departmentName != null || managerId != null || locationId != null
}
