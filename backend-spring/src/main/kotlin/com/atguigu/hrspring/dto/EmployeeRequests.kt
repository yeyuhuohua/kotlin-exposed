package com.atguigu.hrspring.dto
import com.atguigu.hrspring.common.api.ApiException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.LocalDate

/** PUT /api/employees/{id}。未传或显式 null 的列保持原值，不能清空。 */
class EmployeeUpdateRequest {
    var salary: Double? = null
    var departmentId: Int? = null
    var jobId: String? = null
    var phoneNumber: String? = null

    fun hasUpdates(): Boolean =
        salary != null || departmentId != null || jobId != null || phoneNumber != null

    fun contentError(): String? {
        if (jobId != null && jobId!!.isBlank()) return "jobId must not be blank"
        if (jobId != null && jobId!!.exceedsCodePoints(10)) return "jobId must be at most 10 characters"
        if (phoneNumber != null && phoneNumber!!.exceedsCodePoints(20)) {
            return "phoneNumber must be at most 20 characters"
        }
        salary?.let { value -> salaryError(value)?.let { return it } }
        return null
    }
}

/** POST /api/employees。employeeId 由调用方提供。 */
class EmployeeCreateRequest {
    var employeeId: Int? = null
    var firstName: String? = null
    var lastName: String? = null
    var email: String? = null
    var phoneNumber: String? = null
    var hireDate: String? = null
    var jobId: String? = null
    var salary: Double? = null
    var commissionPct: Double? = null
    var managerId: Int? = null
    var departmentId: Int? = null

    fun contentError(): String? {
        if (lastName.isNullOrBlank() || email.isNullOrBlank() || hireDate.isNullOrBlank() || jobId.isNullOrBlank()) {
            return "lastName, email, hireDate, jobId are required"
        }
        if (employeeId == null) return "employeeId is required"
        hireDateError(hireDate!!)?.let { return it }
        if (firstName != null && firstName!!.exceedsCodePoints(20)) return "firstName must be at most 20 characters"
        if (lastName!!.exceedsCodePoints(25)) return "lastName must be at most 25 characters"
        if (email!!.exceedsCodePoints(25)) return "email must be at most 25 characters"
        if (phoneNumber != null && phoneNumber!!.exceedsCodePoints(20)) {
            return "phoneNumber must be at most 20 characters"
        }
        if (jobId!!.exceedsCodePoints(10)) return "jobId must be at most 10 characters"
        salary?.let { value -> salaryError(value)?.let { return it } }
        commissionPct?.let { value -> commissionPctError(value)?.let { return it } }
        return null
    }
}

/**
 * PATCH 只改请求体里出现的字段。显式 null 清空可空列；jobId 不能为 null。
 */
class EmployeePatch(
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
        private val requiredTexts = setOf("jobId")
        private val allowed = nullableNumbers + nullableInts + nullableTexts + requiredTexts

        fun parse(body: JsonNode): EmployeePatch {
            if (body !is ObjectNode) throw ApiException.badRequest("invalid request body")
            if (body.size() == 0) throw ApiException.badRequest("no fields to update")
            val unknown = body.fieldNames().asSequence().filter { it !in allowed }.sorted().toList()
            if (unknown.isNotEmpty()) {
                throw ApiException.badRequest("unknown fields: ${unknown.joinToString(", ")}")
            }
            if (requiredTexts.any { body.has(it) && body.get(it).isNull }) {
                throw ApiException.badRequest("${requiredTexts.first()} must not be null")
            }
            return EmployeePatch(
                present = body.fieldNames().asSequence().toSet(),
                firstName = body.text("firstName")?.also { value ->
                    if (value.exceedsCodePoints(20)) {
                        throw ApiException.badRequest("firstName must be at most 20 characters")
                    }
                },
                salary = body.number("salary")?.also { value ->
                    salaryError(value)?.let { throw ApiException.badRequest(it) }
                },
                commissionPct = body.number("commissionPct")?.also { value ->
                    commissionPctError(value)?.let { throw ApiException.badRequest(it) }
                },
                departmentId = body.integer("departmentId"),
                managerId = body.integer("managerId"),
                phoneNumber = body.text("phoneNumber")?.also { value ->
                    if (value.exceedsCodePoints(20)) {
                        throw ApiException.badRequest("phoneNumber must be at most 20 characters")
                    }
                },
                jobId = body.text("jobId")?.also { value ->
                    if (value.isBlank()) throw ApiException.badRequest("jobId must not be blank")
                    if (value.exceedsCodePoints(10)) {
                        throw ApiException.badRequest("jobId must be at most 10 characters")
                    }
                },
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

private fun salaryError(value: Double): String? =
    if (value.isFinite() && value >= 0) null else "salary must be a non-negative number"

private fun commissionPctError(value: Double): String? =
    if (value.isFinite() && value in 0.0..1.0) null else "commissionPct must be between 0 and 1"

/** MySQL DATE 只接受 1000-01-01 至 9999-12-31。 */
private fun hireDateError(value: String): String? {
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
        ?: return "invalid hireDate, expected yyyy-MM-dd"
    return if (date.year !in 1000..9999) "hireDate must be between 1000-01-01 and 9999-12-31" else null
}

private fun String.exceedsCodePoints(max: Int): Boolean = codePointCount(0, length) > max
