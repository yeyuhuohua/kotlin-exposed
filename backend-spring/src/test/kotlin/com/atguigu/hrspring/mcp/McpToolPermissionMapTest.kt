package com.atguigu.hrspring.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McpToolPermissionMapTest {
    private val expected = mapOf(
        "get_overview" to "api:GET:/api/overview",
        "list_employees" to "api:GET:/api/employees",
        "create_employee" to "api:POST:/api/employees",
        "get_employee" to "api:GET:/api/employees/{id}",
        "update_employee" to "api:PUT:/api/employees/{id}",
        "patch_employee" to "api:PATCH:/api/employees/{id}",
        "delete_employee" to "api:DELETE:/api/employees/{id}",
        "get_employee_details" to "api:GET:/api/employees/{id}/details",
        "list_emp_details" to "api:GET:/api/emp-details",
        "list_departments" to "api:GET:/api/departments",
        "create_department" to "api:POST:/api/departments",
        "get_department" to "api:GET:/api/departments/{id}",
        "update_department" to "api:PUT:/api/departments/{id}",
        "list_department_employees" to "api:GET:/api/departments/{id}/employees",
        "list_jobs" to "api:GET:/api/jobs",
        "create_job" to "api:POST:/api/jobs",
        "update_job" to "api:PUT:/api/jobs/{id}",
        "list_locations" to "api:GET:/api/locations",
        "create_location" to "api:POST:/api/locations",
        "update_location" to "api:PUT:/api/locations/{id}",
        "list_countries" to "api:GET:/api/countries",
        "list_regions" to "api:GET:/api/regions",
        "list_job_history" to "api:GET:/api/job-history",
        "list_job_grades" to "api:GET:/api/job-grades",
        "list_t_dept" to "api:GET:/api/t-dept",
        "create_t_dept" to "api:POST:/api/t-dept",
        "update_t_dept" to "api:PUT:/api/t-dept/{id}",
        "list_t_emp" to "api:GET:/api/t-emp",
        "create_t_emp" to "api:POST:/api/t-emp",
        "update_t_emp" to "api:PUT:/api/t-emp/{id}",
        "list_orders" to "api:GET:/api/orders",
        "list_users" to "api:GET:/api/auth/users",
        "create_user" to "api:POST:/api/auth/users",
        "update_user" to "api:PUT:/api/auth/users/{id}",
        "delete_user" to "api:DELETE:/api/auth/users/{id}",
        "list_roles" to "api:GET:/api/auth/roles",
        "create_role" to "api:POST:/api/auth/roles",
        "update_role" to "api:PUT:/api/auth/roles/{code}",
        "delete_role" to "api:DELETE:/api/auth/roles/{code}",
        "list_permissions" to "api:GET:/api/auth/permissions",
        "get_role_permissions" to "api:GET:/api/auth/roles/{code}/permissions",
        "update_role_permissions" to "api:PUT:/api/auth/roles/{code}/permissions",
        "list_login_records" to "api:GET:/api/audit/logins",
        "list_api_calls" to "api:GET:/api/audit/api-calls",
        "list_menus" to "api:GET:/api/menus",
        "create_menu" to "api:POST:/api/menus",
        "update_menu" to "api:PUT:/api/menus/{key}",
        "delete_menu" to "api:DELETE:/api/menus/{key}",
    )

    @Test
    fun `每个工具的权限码与HTTP接口精确一致`() {
        expected.forEach { (tool, permission) ->
            assertEquals(permission, McpToolPermissions.permissionCode(tool), tool)
        }
        val resources = PathMatchingResourcePatternResolver()
            .getResources("classpath*:com/atguigu/hrspring/mcp/*McpTools.class")
        val registered = resources.flatMap { resource ->
            val simpleName = resource.filename!!.removeSuffix(".class")
            Class.forName("com.atguigu.hrspring.mcp.$simpleName").methods.mapNotNull { method ->
                method.getAnnotation(McpTool::class.java)?.name
            }
        }
        assertEquals(expected.keys, registered.filter { McpToolPermissions.permissionCode(it) != null }.toSet())
    }

    @Test
    fun `登录和退出不再作为MCP工具开放`() {
        assertFalse(McpToolPermissions.isAllowed("login", admin))
        assertFalse(McpToolPermissions.isAllowed("logout", admin))
    }

    private val admin = com.atguigu.hrspring.auth.AuthUser(1, "admin", "ADMIN", true, true, 0, emptySet())
}
