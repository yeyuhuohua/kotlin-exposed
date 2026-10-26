package com.atguigu.hr

import com.atguigu.hr.audit.auditRoutes
import com.atguigu.hr.employee.employeeRoutes
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryValidationTest {
    private fun Application.testModule() {
        install(ContentNegotiation) { json() }
        routing {
            route("/api") {
                employeeRoutes()
                auditRoutes()
            }
        }
    }

    @Test
    fun `非法部门筛选返回400而不是查询全部员工`() = testApplication {
        application { testModule() }
        val response = client.get("/api/employees?departmentId=abc")
        assertEquals(400, response.status.value)
        assertTrue("validation_failed" in response.bodyAsText())
    }

    @Test
    fun `非法登录结果筛选返回400而不是查询全部日志`() = testApplication {
        application { testModule() }
        val response = client.get("/api/audit/logins?success=abc")
        assertEquals(400, response.status.value)
        assertTrue("validation_failed" in response.bodyAsText())
    }
}
