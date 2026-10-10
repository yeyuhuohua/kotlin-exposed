package com.atguigu.hrspring.common.api

/**
 * 校验工具:长度一律按 Unicode 码点计数,与数据库列的字符语义一致。
 */
object Validation {

    fun String.codePointLength(): Int = codePointCount(0, length)

    fun requireNotBlank(value: String?, field: String): String {
        val v = value?.trim()
        if (v.isNullOrEmpty()) throw ApiException.badRequest("$field must not be blank")
        return v
    }

    fun requireMaxLength(value: String?, field: String, max: Int) {
        if (value != null && value.codePointLength() > max) {
            throw ApiException.badRequest("$field must be at most $max characters")
        }
    }

    fun requirePattern(value: String, field: String, regex: Regex, hint: String) {
        if (!regex.matches(value)) throw ApiException.badRequest("$field $hint")
    }

    fun requireNonNegative(value: Double?, field: String) {
        if (value != null && (!value.isFinite() || value < 0)) {
            throw ApiException.badRequest("$field must be a finite number >= 0")
        }
    }

    fun requireRange(value: Double?, field: String, min: Double, max: Double) {
        if (value != null && (!value.isFinite() || value < min || value > max)) {
            throw ApiException.badRequest("$field must be a finite number between $min and $max")
        }
    }

    fun requireRange(value: Int?, field: String, min: Int, max: Int) {
        if (value != null && (value < min || value > max)) {
            throw ApiException.badRequest("$field must be between $min and $max")
        }
    }

    /** MySQL DATE 支持 1000-01-01 至 9999-12-31。 */
    fun requireMysqlDate(value: String, field: String) {
        val date = try {
            java.time.LocalDate.parse(value)
        } catch (e: Exception) {
            throw ApiException.badRequest("$field must be a valid ISO date")
        }
        if (date.year !in 1000..9999) {
            throw ApiException.badRequest("$field year must be between 1000 and 9999")
        }
    }
}
