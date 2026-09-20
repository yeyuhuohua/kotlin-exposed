package com.atguigu.hr.employee

import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

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

/** POST /api/employees。employeeId 由调用方提供。 */@Serializable
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

/** 未分配部门的显示名，聚合查询和前端展示共用。 */
const val UNASSIGNED_DEPARTMENT = "未分配部门"

/** 按部门统计的员工人数。departmentId 为 null 表示未分配部门。 */
@Serializable
@JsonSchema.Description("部门人数分布")
data class DepartmentHeadcountDto(
    @JsonSchema.Description("部门 ID，未分配时为 null")
    val departmentId: Int?,
    @JsonSchema.Description("部门名称")
    val departmentName: String,
    @JsonSchema.Description("该部门员工人数")
    val count: Long,
)

/** 员工薪资汇总，全部由数据库聚合得出。 */
@Serializable
@JsonSchema.Description("薪资汇总")
data class SalarySummaryDto(
    @JsonSchema.Description("有薪资记录的员工数")
    val employeesWithSalary: Long,
    @JsonSchema.Description("薪资总额")
    val totalSalary: Double,
    @JsonSchema.Description("平均薪资，无记录时为 null")
    val averageSalary: Double?,
    @JsonSchema.Description("最低薪资")
    val minSalary: Double?,
    @JsonSchema.Description("最高薪资")
    val maxSalary: Double?,
)

/**
 * PATCH /api/employees/{id} 的部分更新语义：只有请求体里出现的字段才会被修改，
 * 显式传 null 表示把可空列清空（这是 PUT 做不到的）。
 */
data class EmployeePatch(
    val present: Set<String>,
    val firstName: String? = null,
    val salary: Double? = null,
    val commissionPct: Double? = null,
    val departmentId: Int? = null,
    val managerId: Int? = null,
    val phoneNumber: String? = null,
    val jobId: String? = null,
) {
    companion object {
        private val nullableNumbers = setOf("salary", "commissionPct")
        private val nullableInts = setOf("departmentId", "managerId")
        private val nullableTexts = setOf("firstName", "phoneNumber")
        /** jobId 对应非空列，只允许改成另一个非空值。 */
        private val requiredTexts = setOf("jobId")

        /** 未知字段直接拒绝，避免字段名拼错时被静默忽略。 */
        fun parse(body: JsonObject): EmployeePatch {
            if (body.isEmpty()) throw IllegalArgumentException("no fields to update")
            val unknown = body.keys - (nullableNumbers + nullableInts + nullableTexts + requiredTexts)
            require(unknown.isEmpty()) { "unknown fields: ${unknown.sorted().joinToString(", ")}" }
            // job_id 是非空列，显式 null 必须报错而不是被忽略
            require(requiredTexts.none { it in body.keys && body[it] is JsonNull }) {
                "${requiredTexts.first()} must not be null"
            }
            return EmployeePatch(
                present = body.keys.toSet(),
                firstName = body.text("firstName"),
                salary = body.number("salary"),
                commissionPct = body.number("commissionPct"),
                departmentId = body.integer("departmentId"),
                managerId = body.integer("managerId"),
                phoneNumber = body.text("phoneNumber"),
                jobId = body.text("jobId")?.also { require(it.isNotBlank()) { "jobId must not be blank" } },
            )
        }

        private fun JsonObject.element(key: String) = this[key]?.takeIf { it !is JsonNull }

        private fun JsonObject.text(key: String): String? =
            element(key)?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?: validateAbsentOrNull(key)

        // 注意：JsonPrimitive.doubleOrNull 对字符串 "9000" 也会解析成功，必须显式排除字符串。
        private fun JsonObject.number(key: String): Double? = element(key)?.let { value ->
            val primitive = value as? JsonPrimitive
            if (primitive == null || primitive.isString) typeError(key, "number")
            primitive.doubleOrNull ?: typeError(key, "number")
        }

        private fun JsonObject.integer(key: String): Int? = element(key)?.let { value ->
            val primitive = value as? JsonPrimitive
            if (primitive == null || primitive.isString) typeError(key, "integer")
            primitive.intOrNull ?: typeError(key, "integer")
        }

        /** 字段缺失或显式为 null 都返回 null，由 present 决定是否写库。 */
        private fun JsonObject.validateAbsentOrNull(key: String): String? {
            val value = this[key]
            require(value == null || value is JsonNull || (value as? JsonPrimitive)?.isString == true) {
                "$key must be a string or null"
            }
            return null
        }

        private fun typeError(key: String, expected: String): Nothing =
            throw IllegalArgumentException("$key must be a $expected or null")
    }
}
