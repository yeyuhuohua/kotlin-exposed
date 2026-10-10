package com.atguigu.hrspring.dto
import com.atguigu.hrspring.common.api.Validation.requireMaxLength

/** t_dept，示例门派表。 */
data class TDeptDto(
    val id: Int,
    val deptName: String?,
    val address: String?,
)

/** PUT /api/t-dept/{id}，只提交要改的字段。 */
data class TDeptUpdateRequest(
    val deptName: String? = null,
    val address: String? = null,
) {
    fun hasUpdates(): Boolean = deptName != null || address != null

    fun validate() {
        requireMaxLength(deptName, "deptName", 30)
        requireMaxLength(address, "address", 30)
    }
}

/** POST /api/t-dept，id 自增。至少填 deptName 或 address。 */
data class TDeptCreateRequest(
    val deptName: String? = null,
    val address: String? = null,
) {
    fun hasValues(): Boolean = deptName != null || address != null

    fun validate() {
        requireMaxLength(deptName, "deptName", 30)
        requireMaxLength(address, "address", 30)
    }
}
