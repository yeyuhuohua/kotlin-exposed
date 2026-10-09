package com.atguigu.hr.menu

import com.atguigu.hr.auth.AuthUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 菜单入参校验与页面下发过滤规则：绕过界面的直接 API 调用也必须被拦下。 */
class MenuModelsTest {
    @Test
    fun `合法菜单通过校验`() {
        assertNull(MenuCreateRequest(key = "handbook", title = "员工手册", path = "/handbook").contentError())
        assertNull(MenuCreateRequest(key = "a1-2", title = "根路径", path = "/").contentError())
        assertNull(MenuUpdateRequest(title = "员工手册", path = "/handbook", icon = null, group = "", sort = 0).contentError())
    }

    @Test
    fun `菜单 key 拒绝对象：格式非法与前端保留字`() {
        assertNotNull(MenuCreateRequest(key = "1abc", title = "t", path = "/x").contentError())
        assertNotNull(MenuCreateRequest(key = "Abc", title = "t", path = "/x").contentError())
        assertNotNull(MenuCreateRequest(key = "a", title = "t", path = "/x").contentError())
        assertNotNull(MenuCreateRequest(key = "a".repeat(41), title = "t", path = "/x").contentError())
        listOf("login", "app", "account", "forbidden").forEach { reserved ->
            assertNotNull(validateMenuKey(reserved), "保留 key 必须被拒绝：$reserved")
        }
    }

    @Test
    fun `菜单路径拒绝大写、点段与前端保留路径`() {
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "handbook").contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "/Handbook").contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "/a b").contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "/../admin").contentError())
        listOf("/login", "/account", "/forbidden").forEach { reserved ->
            assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = reserved).contentError())
        }
    }

    @Test
    fun `长度按码点计数，增补平面字符不被多数`() {
        // 𠮷 是增补平面字符：UTF-16 占 2 单元但码点数为 1
        val title = "𠮷" + "a".repeat(29)
        assertNull(MenuCreateRequest(key = "xy", title = title, path = "/x").contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = title + "a", path = "/x").contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "/x", group = "g".repeat(21)).contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "/x", sort = 10000).contentError())
        assertNotNull(MenuCreateRequest(key = "xy", title = "t", path = "/x", icon = "1bad").contentError())
    }

    @Test
    fun `页面下发按角色过滤并保持菜单顺序`() {
        val menus = listOf(
            menu("overview", adminOnly = false, enabled = true, sort = 10),
            menu("employees", adminOnly = false, enabled = true, sort = 20),
            menu("audit", adminOnly = true, enabled = true, sort = 30),
            menu("hidden", adminOnly = false, enabled = false, sort = 40),
        )
        val admin = user("ADMIN", linkedSetOf())
        assertEquals(listOf("overview", "employees", "audit"), MenuService.pagesFor(admin, menus).map { it.key })
        val reader = user("READER", linkedSetOf("page:employees", "page:audit", "page:hidden", "page:ghost"))
        assertEquals(listOf("employees"), MenuService.pagesFor(reader, menus).map { it.key })
        assertTrue(MenuService.pagesFor(user("HR_VIEWER", linkedSetOf()), menus).isEmpty())
    }

    @Test
    fun `下发页面携带菜单的展示元数据`() {
        val page = MenuService.pagesFor(user("ADMIN", linkedSetOf()), listOf(menu("overview", false, true, 10))).single()
        assertEquals(PageRoute("overview", "工作概览", "/", "LayoutDashboard", "工作空间", 10), page)
    }

    private fun menu(key: String, adminOnly: Boolean, enabled: Boolean, sort: Int) = MenuDto(
        key = key,
        title = if (key == "overview") "工作概览" else key,
        path = if (key == "overview") "/" else "/$key",
        icon = if (key == "overview") "LayoutDashboard" else null,
        group = if (key == "overview") "工作空间" else "g",
        sort = sort,
        adminOnly = adminOnly,
        builtin = false,
        enabled = enabled,
    )

    private fun user(roleCode: String, permissions: Set<String>) = AuthUser(
        id = 1,
        username = "tester",
        passwordHash = "unused",
        roleCode = roleCode,
        enabled = true,
        roleEnabled = true,
        tokenVersion = 0,
        rolePermissions = permissions,
    )
}
