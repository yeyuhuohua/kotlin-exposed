package com.atguigu.hr.employee

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable

/** employees 表。hireDate 序列化为 ISO 日期字符串。 */
@Serializable
@JsonSchema.Description("员工。hireDate 为 ISO 日期字符串")
data class EmployeeDto(
    @JsonSchema.Description("员工主键 employee_id")
    val employeeId: Int,
    @JsonSchema.Description("名")
    val firstName: String?,
    @JsonSchema.Description("姓")
    val lastName: String,
    @JsonSchema.Description("邮箱账号，库中无域名后缀")
    val email: String,
    @JsonSchema.Description("电话")
    val phoneNumber: String?,
    @JsonSchema.Description("入职日期 yyyy-MM-dd")
    val hireDate: String,
    @JsonSchema.Description("岗位编码，对应 jobs.job_id")
    val jobId: String,
    @JsonSchema.Description("月薪")
    val salary: Double?,
    @JsonSchema.Description("提成比例，销售岗才有")
    val commissionPct: Double?,
    @JsonSchema.Description("直属经理的 employee_id")
    val managerId: Int?,
    @JsonSchema.Description("所属部门 ID")
    val departmentId: Int?,
)

/** 员工详情：JOIN 或 emp_details_view。视图没有 email / phone / hireDate。 */
@Serializable
data class EmployeeDetailDto(
    val employeeId: Int,
    val firstName: String?,
    val lastName: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val hireDate: String? = null,
    val jobId: String,
    val jobTitle: String,
    val salary: Double?,
    val commissionPct: Double?,
    val managerId: Int?,
    val departmentId: Int?,
    val departmentName: String,
    val locationId: Int?,
    val city: String,
    val stateProvince: String?,
    val countryId: String?,
    val countryName: String?,
    val regionName: String?,
)

/** PUT /api/employees/{id} 可更新字段，未传的列保持原值。 */
@Serializable
@JsonSchema.Description("员工部分更新，只提交要改的字段")
data class EmployeeUpdateRequest(
    @JsonSchema.Description("月薪，不传则不改")
    val salary: Double? = null,
    @JsonSchema.Description("所属部门 ID，不传则不改")
    val departmentId: Int? = null,
    @JsonSchema.Description("岗位编码，例如 IT_PROG，不传则不改")
    val jobId: String? = null,
    @JsonSchema.Description("电话，不传则不改")
    val phoneNumber: String? = null,
) {
    fun hasUpdates(): Boolean =
        salary != null || departmentId != null || jobId != null || phoneNumber != null

    fun contentError(): String? =
        if (jobId != null && jobId.isBlank()) "jobId must not be blank" else null
}

/** POST /api/employees。employeeId 由调用方提供。 */
@Serializable
@JsonSchema.Description("新增员工。employeeId 必填")
data class EmployeeCreateRequest(
    @JsonSchema.Description("员工主键，必填")
    val employeeId: Int,
    @JsonSchema.Description("名")
    val firstName: String? = null,
    @JsonSchema.Description("姓，必填")
    val lastName: String,
    @JsonSchema.Description("邮箱账号，库内唯一")
    val email: String,
    @JsonSchema.Description("电话")
    val phoneNumber: String? = null,
    @JsonSchema.Description("入职日期 yyyy-MM-dd")
    val hireDate: String,
    @JsonSchema.Description("岗位编码，例如 IT_PROG")
    val jobId: String,
    @JsonSchema.Description("月薪")
    val salary: Double? = null,
    @JsonSchema.Description("提成比例")
    val commissionPct: Double? = null,
    @JsonSchema.Description("直属经理 employee_id")
    val managerId: Int? = null,
    @JsonSchema.Description("所属部门 ID")
    val departmentId: Int? = null,
)
