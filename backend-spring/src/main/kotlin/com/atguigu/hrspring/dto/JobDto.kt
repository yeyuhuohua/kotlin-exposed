package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.validation.CodePointSize
import com.atguigu.hrspring.common.validation.NotBlankIfPresent
import com.atguigu.hrspring.common.validation.NotDotSegment
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class JobDto(
    val jobId: String,
    val jobTitle: String,
    val minSalary: Int?,
    val maxSalary: Int?,
)

data class JobCreateRequest(
    @field:NotBlank(message = "jobId and jobTitle are required")
    @field:CodePointSize(max = 10, message = "jobId must be at most 10 characters")
    @field:NotDotSegment
    @field:Pattern(
        regexp = """[^/\\\p{Cntrl}]+""",
        message = "jobId must not contain path separators or control characters",
    )
    val jobId: String,
    @field:NotBlank(message = "jobId and jobTitle are required")
    @field:CodePointSize(max = 35, message = "jobTitle must be at most 35 characters")
    val jobTitle: String,
    @field:Min(value = 0, message = "minSalary must be non-negative")
    val minSalary: Int? = null,
    @field:Min(value = 0, message = "maxSalary must be non-negative")
    val maxSalary: Int? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "minSalary must not exceed maxSalary")
    fun isSalaryOrderValid(): Boolean =
        minSalary == null || maxSalary == null || minSalary <= maxSalary
}

data class JobUpdateRequest(
    @field:NotBlankIfPresent(message = "jobTitle must not be blank")
    @field:CodePointSize(max = 35, message = "jobTitle must be at most 35 characters")
    val jobTitle: String? = null,
    @field:Min(value = 0, message = "minSalary must be non-negative")
    val minSalary: Int? = null,
    @field:Min(value = 0, message = "maxSalary must be non-negative")
    val maxSalary: Int? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean =
        jobTitle != null || minSalary != null || maxSalary != null
}
