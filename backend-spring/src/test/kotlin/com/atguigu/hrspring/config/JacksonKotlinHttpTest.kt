package com.atguigu.hrspring.config

import com.atguigu.hrspring.controller.AuthController
import com.atguigu.hrspring.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import com.atguigu.hrspring.dto.MenuCreateRequest
import com.atguigu.hrspring.dto.MenuUpdateRequest
import com.atguigu.hrspring.dto.EmployeeUpdateRequest
import com.atguigu.hrspring.dto.RolePermissionsRequest
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JacksonKotlinHttpTest {
    private fun withMvc(check: (MockMvc, JsonMapper) -> Unit) {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
            .withUserConfiguration(JacksonConfig::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                val mapper = context.getBean(JsonMapper::class.java)
                val mvc = MockMvcBuilders.standaloneSetup(
                    MenuBodyController(),
                    AuthController(mock(AuthService::class.java), context.getBean(ObjectMapper::class.java)),
                )
                    .setControllerAdvice(GlobalExceptionHandler())
                    .setMessageConverters(JacksonJsonHttpMessageConverter(mapper))
                    .build()
                check(mvc, mapper)
            }
    }

    @Test
    fun `HTTP新增菜单保留Kotlin默认值`() = withMvc { mvc, mapper ->
        val response = mvc.perform(post("/probe/menus").contentType("application/json")
            .content("""{"key":"handbook","title":"Handbook","path":"/handbook"}"""))
            .andExpect(status().isOk).andReturn().response
        val body = mapper.readValue(response.contentAsString, MenuCreateRequest::class.java)
        assertEquals("工作空间", body.group)
        assertEquals(100, body.sort)
        assertTrue(body.enabled)
        assertEquals(false, body.adminOnly)
        assertNull(body.icon)
    }

    @Test
    fun `HTTP更新菜单保留默认值并接受显式覆盖`() = withMvc { mvc, mapper ->
        val response = mvc.perform(put("/probe/menus").contentType("application/json")
            .content("""{"title":"Handbook","path":"/handbook"}"""))
            .andExpect(status().isOk).andReturn().response
        val body = mapper.readValue(response.contentAsString, MenuUpdateRequest::class.java)
        assertEquals("工作空间", body.group)
        assertEquals(100, body.sort)
        assertTrue(body.enabled)
        val custom = mapper.readValue(
            """{"title":"Handbook","path":"/handbook","sort":0,"enabled":false}""",
            MenuUpdateRequest::class.java,
        )
        assertEquals(0, custom.sort)
        assertEquals(false, custom.enabled)
    }

    @Test
    fun `HTTP仍拒绝缺失或null的必填Kotlin字段`() = withMvc { mvc, _ ->
        for (json in listOf(
            """{"key":null,"title":"Handbook","path":"/handbook"}""",
            """{"key":"handbook","path":"/handbook"}""",
        )) {
            val response = mvc.perform(post("/probe/menus").contentType("application/json").content(json))
                .andExpect(status().isBadRequest).andReturn().response
            assertTrue(response.contentAsString.contains("validation_failed"))
        }
    }

    @Test
    fun `HTTP拒绝非空数字和布尔字段的显式null`() = withMvc { mvc, _ ->
        for ((path, json) in listOf(
            "/probe/menus" to """{"title":"Handbook","path":"/handbook","enabled":null}""",
            "/probe/menus" to """{"title":"Handbook","path":"/handbook","sort":null}""",
            "/probe/role-permissions" to """{"revision":null,"permissions":[]}""",
        )) {
            val response = mvc.perform(put(path).contentType("application/json").content(json))
                .andExpect(status().isBadRequest).andReturn().response
            assertTrue(response.contentAsString.contains("validation_failed"))
        }
    }

    @Test
    fun `HTTP拒绝把小数截断为部门编号或权限版本号`() = withMvc { mvc, _ ->
        for ((path, json) in listOf(
            "/probe/employees" to """{"departmentId":10.9}""",
            "/probe/role-permissions" to """{"revision":1.9,"permissions":[]}""",
        )) {
            val response = mvc.perform(put(path).contentType("application/json").content(json))
                .andExpect(status().isBadRequest).andReturn().response
            assertTrue(response.contentAsString.contains("validation_failed"))
        }
    }

    @Test
    fun `HTTP拒绝用字符串替代数字或布尔字段`() = withMvc { mvc, _ ->
        for ((path, json) in listOf(
            "/probe/employees" to """{"salary":"9000"}""",
            "/probe/menus" to """{"title":"Handbook","path":"/handbook","enabled":"false"}""",
        )) {
            val response = mvc.perform(put(path).contentType("application/json").content(json))
                .andExpect(status().isBadRequest).andReturn().response
            assertTrue(response.contentAsString.contains("validation_failed"))
        }
    }

    @Test
    fun `HTTP拒绝用数字或布尔值替代文本字段`() = withMvc { mvc, _ ->
        for ((path, json) in listOf(
            "/probe/employees" to """{"jobId":123}""",
            "/probe/menus" to """{"title":true,"path":"/handbook"}""",
        )) {
            val response = mvc.perform(put(path).contentType("application/json").content(json))
                .andExpect(status().isBadRequest).andReturn().response
            assertTrue(response.contentAsString.contains("validation_failed"))
        }
    }

    @Test
    fun `登录接口拒绝非文本账号和密码`() = withMvc { mvc, _ ->
        for (json in listOf(
            """{"username":12345,"password":"password123"}""",
            """{"username":"operator","password":12345678}""",
            """{"username":"operator","password":true}""",
        )) {
            val response = mvc.perform(post("/api/auth/login").contentType("application/json").content(json))
                .andExpect(status().isBadRequest).andReturn().response
            assertTrue(response.contentAsString.contains("validation_failed"))
        }
    }

    @Test
    fun `HTTP仍接受小数薪资整数编号及可空字段`() = withMvc { mvc, mapper ->
        val response = mvc.perform(put("/probe/employees").contentType("application/json")
            .content("""{"salary":9000.5,"departmentId":10,"phoneNumber":null}"""))
            .andExpect(status().isOk).andReturn().response
        val body = mapper.readValue(response.contentAsString, EmployeeUpdateRequest::class.java)
        assertEquals(9000.5, body.salary)
        assertEquals(10, body.departmentId)
        assertNull(body.phoneNumber)
    }
}

@RestController
private class MenuBodyController {
    @PostMapping("/probe/menus")
    fun create(@RequestBody body: MenuCreateRequest) = body

    @PutMapping("/probe/menus")
    fun update(@RequestBody body: MenuUpdateRequest) = body

    @PutMapping("/probe/employees")
    fun updateEmployee(@RequestBody body: EmployeeUpdateRequest) = body

    @PutMapping("/probe/role-permissions")
    fun updatePermissions(@RequestBody body: RolePermissionsRequest) = body
}
