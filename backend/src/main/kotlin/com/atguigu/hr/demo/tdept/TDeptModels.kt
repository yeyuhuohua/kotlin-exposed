package com.atguigu.hr.demo.tdept

import com.atguigu.hr.common.api.exceedsMaxLength
import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** t_dept，示例门派表。 */
@Serializable
data class TDeptDto(
    val id: Int,
    val deptName: String?,
    val address: String?,
)

/** PUT /api/t-dept/{id} */
@Serializable
@JsonSchema.Description("门派部分更新，只提交要改的字段")
data class TDeptUpdateRequest(
    @JsonSchema.Description("门派名，不传则不改")
    val deptName: String? = null,
    @JsonSchema.Description("所在地，不传则不改")
    val address: String? = null,
) {
    fun hasUpdates(): Boolean = deptName != null || address != null

    fun contentError(): String? = when {
        deptName != null && deptName.exceedsMaxLength(30) -> "deptName must be at most 30 characters"
        address != null && address.exceedsMaxLength(30) -> "address must be at most 30 characters"
        else -> null
    }
}

/** POST /api/t-dept，id 自增。 */
@Serializable
@JsonSchema.Description("新增门派，id 由数据库自增")
data class TDeptCreateRequest(
    @JsonSchema.Description("门派名")
    val deptName: String? = null,
    @JsonSchema.Description("所在地")
    val address: String? = null,
) {
    fun hasValues(): Boolean = deptName != null || address != null

    fun contentError(): String? = when {
        deptName != null && deptName.exceedsMaxLength(30) -> "deptName must be at most 30 characters"
        address != null && address.exceedsMaxLength(30) -> "address must be at most 30 characters"
        else -> null
    }
}
