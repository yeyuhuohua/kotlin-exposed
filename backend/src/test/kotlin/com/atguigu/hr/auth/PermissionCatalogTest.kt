package com.atguigu.hr.auth

import com.atguigu.hr.common.api.ErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 权限目录是接口鉴权的唯一依据：漏登记等于静默 403，登记错等于越权。
 * 页面清单已移到菜单表，这里只固定接口码的匹配规则、默认权限和过滤规则。
 */
class PermissionCatalogTest {
    @Test
    fun `按方法与路径匹配已登记的接口权限`() {
        assertEquals("api:GET:/api/employees", PermissionCatalog.requiredApi("GET", "/api/employees")?.code)
        assertEquals("api:POST:/api/employees", PermissionCatalog.requiredApi("POST", "/api/employees")?.code)
        assertEquals("api:GET:/api/employees/{id}", PermissionCatalog.requiredApi("GET", "/api/employees/100")?.code)
        assertEquals("api:PUT:/api/employees/{id}", PermissionCatalog.requiredApi("PUT", "/api/employees/100")?.code)
        assertEquals("api:DELETE:/api/employees/{id}", PermissionCatalog.requiredApi("DELETE", "/api/employees/100")?.code)
        assertEquals(
            "api:GET:/api/employees/{id}/details",
            PermissionCatalog.requiredApi("GET", "/api/employees/100/details")?.code,
        )
        assertEquals(
            "api:PUT:/api/auth/roles/{code}/permissions",
            PermissionCatalog.requiredApi("PUT", "/api/auth/roles/HR_VIEWER/permissions")?.code,
        )
    }

    @Test
    fun `HEAD 按 GET 授权，未登记的路由一律拒绝`() {
        assertEquals("api:GET:/api/employees", PermissionCatalog.requiredApi("HEAD", "/api/employees")?.code)
        assertNull(PermissionCatalog.requiredApi("GET", "/api/secret"))
        assertNull(PermissionCatalog.requiredApi("GET", "/api/employees/100/unknown"))
        assertNull(PermissionCatalog.requiredApi("PATCH", "/api/departments/1"))
        // 员工支持 PATCH，但只登记了员工这一个路径
        assertEquals("api:PATCH:/api/employees/{id}", PermissionCatalog.requiredApi("PATCH", "/api/employees/100")?.code)
    }

    @Test
    fun `菜单管理接口已登记为仅 ADMIN`() {
        assertEquals("api:GET:/api/menus", PermissionCatalog.requiredApi("GET", "/api/menus")?.code)
        assertEquals("api:PUT:/api/menus/{key}", PermissionCatalog.requiredApi("PUT", "/api/menus/handbook")?.code)
        assertEquals("api:DELETE:/api/menus/{key}", PermissionCatalog.requiredApi("DELETE", "/api/menus/handbook")?.code)
        assertTrue(PermissionCatalog.requiredApi("POST", "/api/menus")?.adminOnly == true)
        assertTrue(PermissionCatalog.definitions.filter { it.path.startsWith("/api/menus") }.all { it.adminOnly })
    }

    @Test
    fun `路径参数不分名字都当通配符`() {
        assertTrue(apiPathPattern("/api/employees/{employeeId}").matches("/api/employees/100"))
        assertTrue(apiPathPattern("/api/roles/{code}/permissions").matches("/api/roles/ADMIN/permissions"))
        assertTrue(apiPathPattern("/api/employees").matches("/api/employees"))
        assertFalse(apiPathPattern("/api/employees/{id}").matches("/api/employees/100/details"))
        assertFalse(apiPathPattern("/api/employees").matches("/api/employees/100"))
        // 点号等正则元字符必须按字面量处理
        assertTrue(apiPathPattern("/api/a.b").matches("/api/a.b"))
        assertFalse(apiPathPattern("/api/a.b").matches("/api/axb"))
    }

    @Test
    fun `ADMIN 默认全量接口权限，READER 默认只读接口`() {
        val admin = PermissionCatalog.defaults(RoleCode.ADMIN.name)
        assertEquals(PermissionCatalog.definitions.size, admin.size)
        val reader = PermissionCatalog.defaults(RoleCode.READER.name)
        assertTrue(reader.isNotEmpty())
        assertTrue(reader.all { code ->
            val definition = PermissionCatalog.byCode.getValue(code)
            !definition.adminOnly && definition.method == "GET"
        })
        assertFalse("api:POST:/api/employees" in reader)
        assertFalse("api:GET:/api/menus" in reader)
    }

    @Test
    fun `有效权限过滤未知接口编码与仅管理员权限，页面码放行`() {
        val custom = user(
            roleCode = "HR_VIEWER",
            permissions = linkedSetOf(
                "page:employees",
                "api:GET:/api/employees",
                "page:users",
                "api:GET:/api/auth/users",
                "api:GET:/api/not-registered",
            ),
        )
        assertEquals(
            // 页面码是否存在、是否仅管理员可见，由菜单读取方判定；未知接口码在这里丢弃
            linkedSetOf("page:employees", "api:GET:/api/employees", "page:users"),
            PermissionCatalog.effective(custom),
        )
        assertTrue(custom.hasPermission("api:GET:/api/employees"))
        assertFalse(custom.hasPermission("api:GET:/api/auth/users"))
    }

    @Test
    fun `普通角色权限为空表示拒绝全部，管理员不受存储影响`() {
        assertTrue(PermissionCatalog.effective(user("HR_VIEWER", linkedSetOf())).isEmpty())
        assertEquals(
            PermissionCatalog.definitions.size,
            PermissionCatalog.effective(user(RoleCode.ADMIN.name, linkedSetOf())).size,
        )
    }

    @Test
    fun `停用账号或停用角色时没有任何权限`() {
        val disabledUser = user("HR_VIEWER", linkedSetOf("api:GET:/api/employees")).copy(enabled = false)
        val disabledRole = user("HR_VIEWER", linkedSetOf("api:GET:/api/employees")).copy(roleEnabled = false)
        assertFalse(disabledUser.hasPermission("api:GET:/api/employees"))
        assertFalse(disabledRole.hasPermission("api:GET:/api/employees"))
    }

    @Test
    fun `角色授权校验区分未知编码与仅管理员编码`() {
        val menus = mapOf("employees" to false, "audit" to true)
        assertNull(grantError("api:GET:/api/employees", menus))
        assertNull(grantError("page:employees", menus))
        assertEquals(ErrorCode.UNKNOWN_PERMISSION, grantError("api:GET:/api/nope", menus))
        assertEquals(ErrorCode.UNKNOWN_PERMISSION, grantError("page:nope", menus))
        assertEquals(ErrorCode.ADMIN_ONLY, grantError("api:GET:/api/auth/users", menus))
        assertEquals(ErrorCode.ADMIN_ONLY, grantError("page:audit", menus))
    }

    @Test
    fun `目录本身自洽：编码唯一、方法合法、编码与路径一致`() {
        val codes = PermissionCatalog.definitions.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "存在重复的权限编码")
        val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        PermissionCatalog.definitions.forEach { definition ->
            assertEquals("API", definition.kind, "目录只登记接口权限，页面在菜单表：${definition.code}")
            assertTrue(definition.label.isNotBlank(), "缺少名称：${definition.code}")
            assertTrue(definition.method in methods, "接口权限方法非法：${definition.code}")
            assertTrue(definition.path.startsWith("/api/"), "接口权限路径必须以 /api 开头：${definition.code}")
            assertEquals("api:${definition.method}:${definition.path}", definition.code)
        }
    }
}

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
