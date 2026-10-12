package com.atguigu.hrspring.dto

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** 菜单管理接口的完整记录，对应前端 MenuItem。 */
data class MenuDto(
    val key: String,
    val title: String,
    val path: String,
    val icon: String? = null,
    val group: String,
    val sort: Int,
    val adminOnly: Boolean,
    val builtin: Boolean,
    val enabled: Boolean,
)

/** 下发给前端的可访问页面。前端按 key 映射视图组件。 */
data class PageRoute(
    val key: String,
    val title: String,
    val path: String,
    val icon: String? = null,
    val group: String,
    val sort: Int,
    val adminOnly: Boolean = false,
)

/** 拼进 /api/auth/permissions 的页面权限，字段与前端 PermissionDefinition 对齐。 */
data class MenuPermissionDto(
    val code: String,
    val kind: String,
    val label: String,
    val group: String,
    val path: String,
    val method: String? = null,
    val adminOnly: Boolean = false,
)

/** 新增菜单。adminOnly 创建后不可再改。 */
@ValidMenuCreate
data class MenuCreateRequest(
    val key: String,
    val title: String,
    val path: String,
    val icon: String? = null,
    val group: String = "工作空间",
    val sort: Int = 100,
    val adminOnly: Boolean = false,
    val enabled: Boolean = true,
) {
    fun contentError(): String? = menuKeyError(key) ?: menuPayloadError(title, path, icon, group, sort)
}

/** 整体替换可编辑字段。adminOnly 与 builtin 不在请求里。 */
@ValidMenuUpdate
data class MenuUpdateRequest(
    val title: String,
    val path: String,
    val icon: String? = null,
    val group: String = "工作空间",
    val sort: Int = 100,
    val enabled: Boolean = true,
) {
    fun contentError(): String? = menuPayloadError(title, path, icon, group, sort)
}

private fun String.codePointLength(): Int = codePointCount(0, length)

private val menuKeyPattern = Regex("[a-z][a-z0-9-]{1,39}")
private val menuPathPattern = Regex("/([a-z0-9_-]+(/[a-z0-9_-]+)*)?")
private val menuIconPattern = Regex("[A-Za-z][A-Za-z0-9]{0,39}")

/** 前端静态路由占用的名字与路径，菜单不能抢占。 */
private val reservedMenuKeys = setOf("login", "app", "account", "forbidden")
private val reservedMenuPaths = setOf("/login", "/account", "/forbidden")

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MenuCreateValidator::class])
annotation class ValidMenuCreate(
    val message: String = "invalid menu",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MenuCreateValidator : ConstraintValidator<ValidMenuCreate, MenuCreateRequest> {
    override fun isValid(value: MenuCreateRequest?, context: ConstraintValidatorContext): Boolean {
        val message = value?.contentError() ?: return true
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation()
        return false
    }
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MenuUpdateValidator::class])
annotation class ValidMenuUpdate(
    val message: String = "invalid menu",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MenuUpdateValidator : ConstraintValidator<ValidMenuUpdate, MenuUpdateRequest> {
    override fun isValid(value: MenuUpdateRequest?, context: ConstraintValidatorContext): Boolean {
        val message = value?.contentError() ?: return true
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation()
        return false
    }
}

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [MenuKeyValidator::class])
annotation class MenuKey(
    val message: String = "invalid menu key",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class MenuKeyValidator : ConstraintValidator<MenuKey, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value != null && menuKeyError(value) == null
}

internal fun menuKeyError(key: String): String? {
    if (!menuKeyPattern.matches(key)) {
        return "menu key must contain 2-40 lowercase letters, digits or hyphens and start with a letter"
    }
    if (key in reservedMenuKeys) return "menu key is reserved"
    return null
}

private fun menuPayloadError(title: String, path: String, icon: String?, group: String, sort: Int): String? {
    if (title.isBlank() || title.trim().codePointLength() > 30) {
        return "menu title must contain 1-30 characters"
    }
    if (!menuPathPattern.matches(path) || path.codePointLength() > 100) {
        return "menu path must start with / and contain only lowercase letters, digits, - _ /"
    }
    if (path in reservedMenuPaths) return "menu path is reserved"
    if (icon != null && !menuIconPattern.matches(icon)) {
        return "menu icon must be an icon name (letters and digits)"
    }
    if (group.codePointLength() > 20) return "menu group must contain at most 20 characters"
    if (sort !in 0..9999) return "menu sort must be between 0 and 9999"
    return null
}
