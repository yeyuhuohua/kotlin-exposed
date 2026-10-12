package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.validation.CodePointSize
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min

/** t_emp，示例人物表。 */
data class TEmpDto(
    val id: Int,
    val name: String?,
    val age: Int?,
    val deptId: Int?,
    val empno: Int,
)

/** PUT /api/t-emp/{id}，只提交要改的字段。年龄省略或 0 合法，负数拒绝。 */
data class TEmpUpdateRequest(
    @field:CodePointSize(max = 20, message = "name must be at most 20 characters")
    val name: String? = null,
    @field:Min(value = 0, message = "age must be non-negative")
    val age: Int? = null,
    val deptId: Int? = null,
    val empno: Int? = null,
) {
    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean = name != null || age != null || deptId != null || empno != null
}

/** POST /api/t-emp，id 自增，empno 必填。年龄省略或 0 合法，负数拒绝。 */
data class TEmpCreateRequest(
    @field:CodePointSize(max = 20, message = "name must be at most 20 characters")
    val name: String? = null,
    @field:Min(value = 0, message = "age must be non-negative")
    val age: Int? = null,
    val deptId: Int? = null,
    val empno: Int,
)
