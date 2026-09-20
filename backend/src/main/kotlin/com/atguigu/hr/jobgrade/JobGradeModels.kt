package com.atguigu.hr.jobgrade

import kotlinx.serialization.Serializable

/** job_grades 表。 */
@Serializable
data class JobGradeDto(
    val gradeLevel: String?,
    val lowestSal: Int?,
    val highestSal: Int?,
)
