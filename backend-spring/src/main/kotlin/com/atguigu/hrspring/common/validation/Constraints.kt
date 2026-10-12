package com.atguigu.hrspring.common.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.LocalDate
import java.util.Locale
import kotlin.reflect.KClass

/** 按 Unicode 码点计长度。null 视为未传，交给 @NotNull / @NotBlank。 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [CodePointSizeValidator::class])
annotation class CodePointSize(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val message: String = "length is out of range",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class CodePointSizeValidator : ConstraintValidator<CodePointSize, String> {
    private var min: Int = 0
    private var max: Int = Int.MAX_VALUE

    override fun initialize(constraint: CodePointSize) {
        min = constraint.min
        max = constraint.max
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        return value.codePointCount(0, value.length) in min..max
    }
}

/** 更新请求里字段可以不传；传了就不能是空白。 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NotBlankIfPresentValidator::class])
annotation class NotBlankIfPresent(
    val message: String = "must not be blank",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NotBlankIfPresentValidator : ConstraintValidator<NotBlankIfPresent, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value.isNotBlank()
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [FiniteMinValidator::class])
annotation class FiniteMin(
    val value: Double = 0.0,
    val message: String = "must be a finite number",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class FiniteMinValidator : ConstraintValidator<FiniteMin, Double> {
    private var min: Double = 0.0

    override fun initialize(constraint: FiniteMin) {
        min = constraint.value
    }

    override fun isValid(value: Double?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        return value.isFinite() && value >= min
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [FiniteRangeValidator::class])
annotation class FiniteRange(
    val min: Double = 0.0,
    val max: Double = 1.0,
    val message: String = "must be inside the range",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class FiniteRangeValidator : ConstraintValidator<FiniteRange, Double> {
    private var min: Double = 0.0
    private var max: Double = 1.0

    override fun initialize(constraint: FiniteRange) {
        min = constraint.min
        max = constraint.max
    }

    override fun isValid(value: Double?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        return value.isFinite() && value in min..max
    }
}

/** MySQL DATE：能解析，并且年份在 1000 到 9999。 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HireDateValidator::class])
annotation class HireDate(
    val message: String = "invalid hireDate, expected yyyy-MM-dd",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class HireDateValidator : ConstraintValidator<HireDate, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val date = runCatching { LocalDate.parse(value) }.getOrNull()
        if (date == null) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate("invalid hireDate, expected yyyy-MM-dd")
                .addConstraintViolation()
            return false
        }
        if (date.year !in 1000..9999) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate("hireDate must be between 1000-01-01 and 9999-12-31")
                .addConstraintViolation()
            return false
        }
        return true
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NotDotSegmentValidator::class])
annotation class NotDotSegment(
    val message: String = "jobId must not be '.' or '..'",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NotDotSegmentValidator : ConstraintValidator<NotDotSegment, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || (value != "." && value != "..")
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [UsernameValidator::class])
annotation class Username(
    val message: String = "username must contain 3-64 ASCII letters, digits, dots, underscores or hyphens",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class UsernameValidator : ConstraintValidator<Username, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return false
        return USERNAME_PATTERN.matches(value.trim().lowercase(Locale.ROOT))
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PasswordValidator::class])
annotation class Password(
    val message: String = "password must contain 8-128 characters and must not be blank",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** null 表示更新请求没改密码。空白或长度不对仍然拒绝。 */
class PasswordValidator : ConstraintValidator<Password, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        return value.length in 8..128 && value.isNotBlank()
    }
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [RoleCodeValidator::class])
annotation class RoleCode(
    val message: String = "invalid role code",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class RoleCodeValidator : ConstraintValidator<RoleCode, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return false
        return ROLE_CODE_PATTERN.matches(value.trim().uppercase(Locale.ROOT))
    }
}

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [RoleNameValidator::class])
annotation class RoleName(
    val message: String = "role name must contain 1-50 characters",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

/** null 表示更新请求没改名称。 */
class RoleNameValidator : ConstraintValidator<RoleName, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val trimmed = value.trim()
        return trimmed.isNotBlank() && trimmed.codePointCount(0, trimmed.length) <= 50
    }
}

private val USERNAME_PATTERN = Regex("[a-z0-9._-]{3,64}")
private val ROLE_CODE_PATTERN = Regex("[A-Z][A-Z0-9_]{1,19}")
