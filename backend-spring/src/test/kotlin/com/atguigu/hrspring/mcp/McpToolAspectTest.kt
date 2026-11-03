package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.CurrentUserHolder
import com.atguigu.hrspring.auth.Roles
import com.atguigu.hrspring.common.api.ApiException
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.ai.mcp.annotation.McpTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class McpToolAspectTest {
    @Test
    fun `未授权调用不会进入工具方法`() {
        val target = DummyEmployeeTools()
        val proxy = AspectJProxyFactory(target).apply {
            addAspect(McpToolAuthorizationAspect())
        }.getProxy() as DummyEmployeeTools
        val reader = AuthUser(2, "reader", Roles.READER, true, true, 0, emptySet())
        CurrentUserHolder.set(reader)
        try {
            assertFailsWith<ApiException> { proxy.createEmployee() }
            assertEquals(0, target.calls)
        } finally {
            CurrentUserHolder.clear()
        }
    }
}

open class DummyEmployeeTools {
    var calls = 0

    @McpTool(name = "create_employee", description = "测试工具")
    open fun createEmployee(): String {
        calls++
        return "created"
    }
}
