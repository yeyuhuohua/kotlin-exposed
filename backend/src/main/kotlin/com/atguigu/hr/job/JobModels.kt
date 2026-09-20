package com.atguigu.hr.job

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** jobs 表。 */
@Serializable
data class JobDto(
    val jobId: String,
    val jobTitle: String,
    val minSalary: Int?,
    val maxSalary: Int?,
)

/** PUT /api/jobs/{id}，路径 id 为 job_id。 */
@Serializable
@JsonSchema.Description("岗位部分更新，只提交要改的字段")
data class JobUpdateRequest(
    @JsonSchema.Description("岗位名称，不传则不改")
    val jobTitle: String? = null,
    @JsonSchema.Description("最低月薪，不传则不改")
    val minSalary: Int? = null,
    @JsonSchema.Description("最高月薪，不传则不改")
    val maxSalary: Int? = null,
) {
    fun hasUpdates(): Boolean =
        jobTitle != null || minSalary != null || maxSalary != null

    fun contentError(): String? =
        if (jobTitle != null && jobTitle.isBlank()) "jobTitle must not be blank" else null
}

/** POST /api/jobs，jobId 为业务主键。 */
@Serializable
@JsonSchema.Description("新增岗位，jobId 必填")
data class JobCreateRequest(
    @JsonSchema.Description("岗位编码，主键，例如 KT_DEV")
    val jobId: String,
    @JsonSchema.Description("岗位名称，必填")
    val jobTitle: String,
    @JsonSchema.Description("最低月薪")
    val minSalary: Int? = null,
    @JsonSchema.Description("最高月薪")
    val maxSalary: Int? = null,
)
