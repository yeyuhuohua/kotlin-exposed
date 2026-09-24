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

    fun contentError(): String? = when {
        jobTitle != null && jobTitle.isBlank() -> "jobTitle must not be blank"
        minSalary != null && minSalary < 0 -> "minSalary must be non-negative"
        maxSalary != null && maxSalary < 0 -> "maxSalary must be non-negative"
        else -> null
    }

    /** 与库里现值合并后的区间校验：只改一端时，另一端取现值比对。 */
    fun rangeError(current: JobDto): String? {
        val min = minSalary ?: current.minSalary
        val max = maxSalary ?: current.maxSalary
        return if (min != null && max != null && min > max) "minSalary must not exceed maxSalary" else null
    }
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
) {
    fun contentError(): String? = when {
        jobId.isBlank() || jobTitle.isBlank() -> "jobId and jobTitle are required"
        minSalary != null && minSalary < 0 -> "minSalary must be non-negative"
        maxSalary != null && maxSalary < 0 -> "maxSalary must be non-negative"
        minSalary != null && maxSalary != null && minSalary > maxSalary -> "minSalary must not exceed maxSalary"
        else -> null
    }
}
