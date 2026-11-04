package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.common.api.ApiException
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class McpToolAuthorizationTest {
    private val admin = AuthUser(1, "admin", Roles.ADMIN, true, true, 0, emptySet())
    private val reader = AuthUser(
        2, "reader", Roles.READER, true, true, 0,
        setOf("api:GET:/api/employees"),
    )

    @AfterTest
    fun clearUser() = CurrentUserHolder.clear()

    @Test
    fun `普通用户只能调用已授权接口对应的工具`() {
        CurrentUserHolder.set(reader)
        McpToolPermissions.authorize("list_employees")
        val error = assertFailsWith<ApiException> {
            McpToolPermissions.authorize("create_employee")
        }
        assertEquals(403, error.status)
        assertEquals("forbidden", error.error)
    }

    @Test
    fun `登录即用工具不要求额外接口授权`() {
        CurrentUserHolder.set(reader.copy(rolePermissions = emptySet()))
        McpToolPermissions.authorize("get_current_user")
        McpToolPermissions.authorize("get_health")
        McpToolPermissions.authorize("export_excel")
    }

    @Test
    fun `未登记工具即使管理员也不能调用`() {
        CurrentUserHolder.set(admin)
        assertFailsWith<ApiException> { McpToolPermissions.authorize("unknown_tool") }
    }

    @Test
    fun `所有已注册MCP工具都有权限映射`() {
        val resources = PathMatchingResourcePatternResolver()
            .getResources("classpath*:com/atguigu/hrspring/mcp/*McpTools.class")
        val methods = resources.flatMap { resource ->
            val simpleName = resource.filename!!.removeSuffix(".class")
            Class.forName("com.atguigu.hrspring.mcp.$simpleName").methods.filter { method ->
                method.getAnnotation(McpTool::class.java) != null
            }
        }
        val names = methods.map { it.getAnnotation(McpTool::class.java).name }
        assertTrue(names.isNotEmpty())
        assertEquals(names.size, names.distinct().size)
        assertTrue(methods.all {
            org.springframework.ai.mcp.annotation.context.McpSyncRequestContext::class.java in it.parameterTypes
        })
        CurrentUserHolder.set(admin)
        names.forEach(McpToolPermissions::authorize)
    }
}
