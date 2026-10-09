package com.atguigu.hr.menu

import com.atguigu.hr.common.api.exceedsMaxLength
import kotlinx.serialization.Serializable

/** 菜单管理接口的数据契约与入参校验；长度按 Unicode 码点计，与列定义一致。 */

@Serializable
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

/** 下发给前端的可访问页面：前端按 key 映射视图组件，不再自行声明路由清单。 */
@Serializable
data class PageRoute(
    val key: String,
    val title: String,
    val path: String,
    val icon: String? = null,
    val group: String,
    val sort: Int,
    val adminOnly: Boolean = false,
)

@Serializable
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
    fun contentError(): String? = validateMenuKey(key) ?: validateMenuPayload(title, path, icon, group, sort)
}

/** 整体替换可编辑字段；adminOnly 创建时定死后不再修改（避免角色已持有的授权变成越权）。 */
@Serializable
data class MenuUpdateRequest(
    val title: String,
    val path: String,
    val icon: String? = null,
    val group: String = "工作空间",
    val sort: Int = 100,
    val enabled: Boolean = true,
) {
    fun contentError(): String? = validateMenuPayload(title, path, icon, group, sort)
}

private val menuKeyPattern = Regex("[a-z][a-z0-9-]{1,39}")
private val menuPathPattern = Regex("/([a-z0-9_-]+(/[a-z0-9_-]+)*)?")
private val menuIconPattern = Regex("[A-Za-z][A-Za-z0-9]{0,39}")

/** 前端静态路由占用的名字与路径，菜单不能抢占，否则动态注册会冲突。 */
private val reservedMenuKeys = setOf("login", "app", "account", "forbidden")
private val reservedMenuPaths = setOf("/login", "/account", "/forbidden")

fun validateMenuKey(key: String): String? {
    if (!menuKeyPattern.matches(key)) return "menu key must contain 2-40 lowercase letters, digits or hyphens and start with a letter"
    if (key in reservedMenuKeys) return "menu key is reserved"
    return null
}

private fun validateMenuPayload(title: String, path: String, icon: String?, group: String, sort: Int): String? {
    if (title.isBlank() || title.trim().exceedsMaxLength(30)) return "menu title must contain 1-30 characters"
    if (!menuPathPattern.matches(path) || path.exceedsMaxLength(100)) {
        return "menu path must start with / and contain only lowercase letters, digits, - _ /"
    }
    if (path in reservedMenuPaths) return "menu path is reserved"
    if (icon != null && !menuIconPattern.matches(icon)) return "menu icon must be an icon name (letters and digits)"
    if (group.exceedsMaxLength(20)) return "menu group must contain at most 20 characters"
    if (sort !in 0..9999) return "menu sort must be between 0 and 9999"
    return null
}
