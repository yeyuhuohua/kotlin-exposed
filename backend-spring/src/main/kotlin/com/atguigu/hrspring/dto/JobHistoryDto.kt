package com.atguigu.hrspring.dto
data class JobHistoryDto(
    val employeeId: Int,
    val startDate: String,
    val endDate: String,
    val jobId: String,
    val departmentId: Int?,
)
