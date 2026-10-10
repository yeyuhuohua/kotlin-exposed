package com.atguigu.hrspring.dto
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.Validation.requireMaxLength

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
    val name: String? = null,
    val age: Int? = null,
    val deptId: Int? = null,
    val empno: Int? = null,
) {
    fun hasUpdates(): Boolean = name != null || age != null || deptId != null || empno != null

    fun validate() {
        requireMaxLength(name, "name", 20)
        if (age != null && age < 0) {
            throw ApiException.badRequest("age must be non-negative")
        }
    }
}

/** POST /api/t-emp，id 自增，empno 必填。年龄省略或 0 合法，负数拒绝。 */
data class TEmpCreateRequest(
    val name: String? = null,
    val age: Int? = null,
    val deptId: Int? = null,
    val empno: Int,
) {
    fun validate() {
        requireMaxLength(name, "name", 20)
        if (age != null && age < 0) {
            throw ApiException.badRequest("age must be non-negative")
        }
    }
}
