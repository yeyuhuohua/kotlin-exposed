package com.atguigu.hr.jobhistory

import kotlinx.serialization.Serializable

/** job_history 表。 */
@Serializable
data class JobHistoryDto(
    val employeeId: Int,
    val startDate: String,
    val endDate: String,
    val jobId: String,
    val departmentId: Int?,
)
