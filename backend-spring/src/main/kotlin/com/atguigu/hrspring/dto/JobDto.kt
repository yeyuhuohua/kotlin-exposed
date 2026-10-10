package com.atguigu.hrspring.dto
data class JobDto(
    val jobId: String,
    val jobTitle: String,
    val minSalary: Int?,
    val maxSalary: Int?,
)

data class JobCreateRequest(
    val jobId: String,
    val jobTitle: String,
    val minSalary: Int? = null,
    val maxSalary: Int? = null,
)

data class JobUpdateRequest(
    val jobTitle: String? = null,
    val minSalary: Int? = null,
    val maxSalary: Int? = null,
) {
    fun hasUpdates(): Boolean =
        jobTitle != null || minSalary != null || maxSalary != null
}
