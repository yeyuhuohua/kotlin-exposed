package com.atguigu.hrspring.dto

import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.validation.CodePointSize
import com.atguigu.hrspring.common.validation.FiniteMin
import com.atguigu.hrspring.common.validation.FiniteRange
import com.atguigu.hrspring.common.validation.HireDate
import com.atguigu.hrspring.common.validation.NotBlankIfPresent
import com.fasterxml.jackson.annotation.JsonIgnore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** PUT /api/employees/{id}。未传或显式 null 的列保持原值，不能清空。 */
class EmployeeUpdateRequest {
    @field:FiniteMin(message = "salary must be a non-negative number")
    var salary: Double? = null
    var departmentId: Int? = null

    @field:NotBlankIfPresent(message = "jobId must not be blank")
    @field:CodePointSize(max = 10, message = "jobId must be at most 10 characters")
    var jobId: String? = null

    @field:CodePointSize(max = 20, message = "phoneNumber must be at most 20 characters")
    var phoneNumber: String? = null

    @JsonIgnore
    @AssertTrue(message = "no fields to update")
    fun hasUpdates(): Boolean =
        salary != null || departmentId != null || jobId != null || phoneNumber != null
}

/** POST /api/employees。employeeId 由调用方提供。 */
class EmployeeCreateRequest {
    @field:NotNull(message = "employeeId is required")
    var employeeId: Int? = null

    @field:CodePointSize(max = 20, message = "firstName must be at most 20 characters")
    var firstName: String? = null

    @field:NotBlank(message = "lastName, email, hireDate, jobId are required")
    @field:CodePointSize(max = 25, message = "lastName must be at most 25 characters")
    var lastName: String? = null

    @field:NotBlank(message = "lastName, email, hireDate, jobId are required")
    @field:CodePointSize(max = 25, message = "email must be at most 25 characters")
    var email: String? = null

    @field:CodePointSize(max = 20, message = "phoneNumber must be at most 20 characters")
    var phoneNumber: String? = null

    @field:NotBlank(message = "lastName, email, hireDate, jobId are required")
    @field:HireDate
    var hireDate: String? = null

    @field:NotBlank(message = "lastName, email, hireDate, jobId are required")
    @field:CodePointSize(max = 10, message = "jobId must be at most 10 characters")
    var jobId: String? = null

    @field:FiniteMin(message = "salary must be a non-negative number")
    var salary: Double? = null

    @field:FiniteRange(message = "commissionPct must be between 0 and 1")
    var commissionPct: Double? = null
    var managerId: Int? = null
    var departmentId: Int? = null
}

/**
 * PATCH 只改请求体里出现的字段。显式 null 清空可空列；jobId 不能为 null。
 */
class EmployeePatch(
    val present: Set<String>,
    @field:CodePointSize(max = 20, message = "firstName must be at most 20 characters")
    val firstName: String? = null,
    @field:FiniteMin(message = "salary must be a non-negative number")
    val salary: Double? = null,
    @field:FiniteRange(message = "commissionPct must be between 0 and 1")
    val commissionPct: Double? = null,
    val departmentId: Int? = null,
    val managerId: Int? = null,
    @field:CodePointSize(max = 20, message = "phoneNumber must be at most 20 characters")
    val phoneNumber: String? = null,
    @field:NotBlankIfPresent(message = "jobId must not be blank")
    @field:CodePointSize(max = 10, message = "jobId must be at most 10 characters")
    val jobId: String? = null,
) {
    companion object {
        private val nullableNumbers = setOf("salary", "commissionPct")
        private val nullableInts = setOf("departmentId", "managerId")
        private val nullableTexts = setOf("firstName", "phoneNumber")
        private val requiredTexts = setOf("jobId")
        private val allowed = nullableNumbers + nullableInts + nullableTexts + requiredTexts

        fun parse(body: JsonNode): EmployeePatch {
            if (body !is ObjectNode) throw ApiException.badRequest("invalid request body")
            if (body.size() == 0) throw ApiException.badRequest("no fields to update")
            val names = body.propertyNames()
            val unknown = names.filter { it !in allowed }.sorted()
            if (unknown.isNotEmpty()) {
                throw ApiException.badRequest("unknown fields: ${unknown.joinToString(", ")}")
            }
            if (requiredTexts.any { body.has(it) && body.get(it).isNull }) {
                throw ApiException.badRequest("${requiredTexts.first()} must not be null")
            }
            return EmployeePatch(
                present = names.toSet(),
                firstName = body.text("firstName"),
                salary = body.number("salary"),
                commissionPct = body.number("commissionPct"),
                departmentId = body.integer("departmentId"),
                managerId = body.integer("managerId"),
                phoneNumber = body.text("phoneNumber"),
                jobId = body.text("jobId"),
            )
        }

        private fun JsonNode.text(key: String): String? {
            if (!has(key)) return null
            val node = get(key)
            if (node == null || node.isNull) return null
            if (!node.isTextual) throw ApiException.badRequest("$key must be a string or null")
            return node.asText()
        }

        /** 字符串形式的数字（"9000"）必须拒绝，不能当成数字。 */
        private fun JsonNode.number(key: String): Double? {
            if (!has(key)) return null
            val node = get(key)
            if (node == null || node.isNull) return null
            if (node.isTextual || !node.isNumber) {
                throw ApiException.badRequest("$key must be a number or null")
            }
            return node.doubleValue()
        }

        private fun JsonNode.integer(key: String): Int? {
            if (!has(key)) return null
            val node = get(key)
            if (node == null || node.isNull) return null
            if (node.isTextual || !node.isIntegralNumber || !node.canConvertToInt()) {
                throw ApiException.badRequest("$key must be a integer or null")
            }
            return node.intValue()
        }
    }
}
