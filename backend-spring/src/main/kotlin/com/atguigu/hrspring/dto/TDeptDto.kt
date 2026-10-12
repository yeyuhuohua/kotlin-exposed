package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.validation.CodePointSize
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue

/** t_dept，示例门派表。 */
data class TDeptDto(
    val id: Int,
    val deptName: String?,
    val address: String?,
)

/** PUT /api/t-dept/{id}，只提交要改的字段。 */
data class TDeptUpdateRequest(
    @field:CodePointSize(max = 30, message = "deptName must be at most 30 characters")
    val deptName: String? = null,
    @field:CodePointSize(max = 30, message = "address must be at most 30 characters")
    val address: String? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean = deptName != null || address != null
}

/** POST /api/t-dept，id 自增。至少填 deptName 或 address。 */
data class TDeptCreateRequest(
    @field:CodePointSize(max = 30, message = "deptName must be at most 30 characters")
    val deptName: String? = null,
    @field:CodePointSize(max = 30, message = "address must be at most 30 characters")
    val address: String? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "deptName or address is required")
    fun hasValues(): Boolean = deptName != null || address != null
}
