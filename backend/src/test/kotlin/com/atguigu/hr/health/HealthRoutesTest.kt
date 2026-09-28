package com.atguigu.hr.health

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** 依赖不可用时返回 503 + 稳定错误码 dependency_unavailable，健康详情保留在 data 里。 */
class HealthRoutesTest {
    @Test
    fun `依赖不可用时返回 503 与稳定错误码`() = testApplication {
        // 测试环境不初始化 DatabaseFactory/RedisFactory，两个依赖必然 DOWN
        application {
            install(ContentNegotiation) { json() }
            routing { healthRoutes() }
        }
        val response = client.get("/health")
        assertEquals(503, response.status.value)
        val envelope = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("dependency_unavailable", envelope["error"]?.jsonPrimitive?.content)
        val data = envelope["data"]?.jsonObject
        assertEquals("DOWN", data?.get("status")?.jsonPrimitive?.content)
        assertEquals("DOWN", data?.get("database")?.jsonPrimitive?.content)
        assertEquals("DOWN", data?.get("redis")?.jsonPrimitive?.content)
    }
}
