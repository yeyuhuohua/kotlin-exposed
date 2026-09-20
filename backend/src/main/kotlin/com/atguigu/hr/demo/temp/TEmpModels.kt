package com.atguigu.hr.demo.temp

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** t_emp，示例人物表。 */
@Serializable
data class TEmpDto(
    val id: Int,
    val name: String?,
    val age: Int?,
    val deptId: Int?,
    val empno: Int,
)

/** PUT /api/t-emp/{id} */
@Serializable
@JsonSchema.Description("人物部分更新，只提交要改的字段")
data class TEmpUpdateRequest(
    @JsonSchema.Description("姓名，不传则不改")
    val name: String? = null,
    @JsonSchema.Description("年龄，不传则不改")
    val age: Int? = null,
    @JsonSchema.Description("所属门派 t_dept.id，不传则不改")
    val deptId: Int? = null,
    @JsonSchema.Description("工号 empno，不传则不改")
    val empno: Int? = null,
) {
    fun hasUpdates(): Boolean =
        name != null || age != null || deptId != null || empno != null
}

/** POST /api/t-emp，id 自增，empno 必填。 */
@Serializable
@JsonSchema.Description("新增人物，id 由数据库自增")
data class TEmpCreateRequest(
    @JsonSchema.Description("姓名")
    val name: String? = null,
    @JsonSchema.Description("年龄")
    val age: Int? = null,
    @JsonSchema.Description("所属门派 t_dept.id")
    val deptId: Int? = null,
    @JsonSchema.Description("工号 empno，必填")
    val empno: Int,
)
