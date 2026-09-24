package com.atguigu.hr.audit

import com.atguigu.hr.common.api.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * API 调用审计插件的行为：记录方法、路径、查询串、状态码、耗时与真实客户端 IP；
 * /api/health 等排除路径不记录；未认证请求用户字段为 null。
 */
class ApiCallAuditPluginTest {
    private val collected = mutableListOf<ApiCallRecordInput>()

    private fun testModule(): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        install(ApiCallAudit) {
            sink = { collected.add(it) }
        }
        routing {
            get("/api/demo") { call.respondOk("ok") }
            get("/api/health") { call.respondOk("up") }
        }
    }

    @Test
    fun `记录方法路径状态码耗时与查询串`() = testApplication {
        application(testModule())
        client.get("/api/demo?limit=50&offset=0")
        val record = collected.single()
        assertEquals("GET", record.method)
        assertEquals("/api/demo", record.path)
        assertEquals("limit=50&offset=0", record.queryString)
        assertEquals(200, record.statusCode)
        assertTrue(record.durationMs >= 0)
        assertNull(record.userId)
        assertNull(record.username)
    }

    @Test
    fun `排除路径不记录`() = testApplication {
        application(testModule())
        client.get("/api/health")
        assertTrue(collected.isEmpty(), "/api/health 是高频轮询，不应进入审计表")
    }

    @Test
    fun `本机直连视为可信代理，取转发链中第一个不可信跳`() = testApplication {
        application(testModule())
        // 测试客户端是本机回环（默认可信），X-Forwarded-For 链中右侧的 10.0.0.2 不可信。
        client.get("/api/demo") { header(HttpHeaders.XForwardedFor, "203.0.113.7, 10.0.0.2") }
        assertEquals("10.0.0.2", collected.single().ip)
    }

    @Test
    fun `本机直连带单跳转发头时记录该客户端`() = testApplication {
        application(testModule())
        client.get("/api/demo") { header(HttpHeaders.XForwardedFor, "203.0.113.7") }
        assertEquals("203.0.113.7", collected.single().ip)
    }

    @Test
    fun `记录内容协商后的最终状态码`() = testApplication {
        application(testModule())
        // 只支持 JSON 的接口收到 XML 的 Accept 会被内容协商转成 406，审计必须记录 406 而不是 200。
        val response = client.get("/api/demo") { header(HttpHeaders.Accept, "application/xml") }
        assertEquals(406, response.status.value)
        assertEquals(406, collected.single().statusCode)
    }

    @Test
    fun `路径过滤纯逻辑`() {
        assertTrue(shouldAuditApiCall("/api/employees"))
        assertTrue(!shouldAuditApiCall("/api/health"))
        assertTrue(!shouldAuditApiCall("/swagger"))
        assertTrue(!shouldAuditApiCall("/doc.html"))
    }
}
