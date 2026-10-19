package com.atguigu.hrspring.config

import com.atguigu.hrspring.audit.AuditFilterConfig
import com.atguigu.hrspring.audit.AuditService
import com.atguigu.hrspring.auth.AuthUser
import com.atguigu.hrspring.auth.AuthUserLoader
import com.atguigu.hrspring.auth.JwtAuthFilter
import com.atguigu.hrspring.auth.PermissionInterceptor
import com.atguigu.hrspring.auth.TokenService
import com.atguigu.hrspring.common.api.ClientIp
import com.atguigu.hrspring.mcp.McpSecurityConfig
import tools.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.FilterDef
import org.apache.tomcat.util.descriptor.web.FilterMap
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServletFiltersTest {
    @Test
    fun `真实servlet映射下接口保留安全头和审计而健康检查及文档不写审计`() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve(".git")) }
        val debug = Files.createDirectories(root.resolve(".debug"))
        val directory = Files.createTempDirectory(debug, "servlet-filters-")
        val server = Tomcat().apply {
            setBaseDir(directory.toString())
            setPort(0)
            connector.setProperty("address", "127.0.0.1")
        }
        try {
            val properties = HrProperties(auth = HrProperties.AuthProperties(jwtSecret = "a".repeat(32)))
            val tokens = TokenService(properties)
            val user = AuthUser(7, "reader", "READER", true, true, 0, emptySet())
            val loader = object : AuthUserLoader {
                override fun loadActiveUser(userId: Int, tokenVersion: Int): AuthUser? {
                    if (userId == 99) throw IllegalStateException("authentication dependency unavailable")
                    return user.takeIf { it.id == userId && it.tokenVersion == tokenVersion }
                }
                override fun loadActiveAdmin(): AuthUser? = null
            }
            val audit = mock(AuditService::class.java)
            val mcpProperties = McpServerStreamableHttpProperties().apply { mcpEndpoint = "/api/mcp" }
            val context = server.addContext("/hr", directory.toString())
            Tomcat.addServlet(context, "gateway", object : HttpServlet() {
                override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                    response.status = 200
                }
            })
            context.addServletMappingDecoded("/gateway/*", "gateway")
            context.addServletMappingDecoded("/", "gateway")
            val web = WebConfig(properties, PermissionInterceptor())
            val filters = listOf(
                web.securityHeadersFilter(),
                web.corsFilter(),
                AuditFilterConfig(audit, ClientIp(properties)).auditFilter(),
                McpSecurityConfig(tokens, loader, properties, jacksonObjectMapper(), mcpProperties).mcpSecurityFilter(),
                FilterRegistrationBean(JwtAuthFilter(tokens, loader, jacksonObjectMapper())).apply {
                    addUrlPatterns("/*")
                    order = 100
                },
            )
            filters.sortedBy { it.order }.forEachIndexed { index, registration ->
                val name = "filter-$index"
                context.addFilterDef(FilterDef().apply {
                    filterName = name
                    filter = registration.filter
                })
                context.addFilterMap(FilterMap().apply {
                    filterName = name
                    registration.urlPatterns.forEach { addURLPattern(it) }
                })
            }
            server.start()
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            fun request(path: String, token: String? = null, origin: String? = null, method: String = "GET"): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:${server.connector.localPort}/hr$path"),
                ).timeout(Duration.ofSeconds(5))
                if (token != null) builder.header("Authorization", "Bearer $token")
                if (origin != null) builder.header("Origin", origin)
                if (method == "OPTIONS") {
                    builder.header("Access-Control-Request-Method", "GET")
                    builder.header("Access-Control-Request-Headers", "authorization")
                }
                return client.send(
                    builder.method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString(),
                )
            }
            fun records() = mockingDetails(audit).invocations.filter { it.method.name == "recordApiCall" }

            for (prefix in listOf("", "/gateway")) {
                val origin = "http://localhost:5173"
                val beforePreflight = records().size
                val preflight = request("$prefix/api/jobs", origin = origin, method = "OPTIONS")
                assertEquals(200, preflight.statusCode())
                assertEquals(origin, preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
                assertEquals(beforePreflight, records().size)
                assertEquals(400, request("$prefix/api/jobs/DEV%2FOPS", tokens.issue(7, 0)).statusCode())
                for (path in listOf("/api/jobs", "/%61pi/jobs", "/api;v=1/jobs")) {
                    val before = records().size
                    val response = request(prefix + path, tokens.issue(7, 0))
                    assertEquals(200, response.statusCode())
                    assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null))
                    assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(null))
                    assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElse(null))
                    assertEquals(before + 1, records().size)
                    assertEquals(7, records().last().arguments[0])
                    assertEquals("/hr$prefix$path", records().last().arguments[3])
                }
                val response = request("$prefix/api/jobs", "invalid", origin)
                assertEquals(401, response.statusCode())
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
                assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null))
                assertEquals(401, records().last().arguments[5])
                assertNull(records().last().arguments[0])
                val beforeMcp = records().size
                assertEquals(401, request("$prefix/api/mcp").statusCode())
                assertEquals(beforeMcp + 1, records().size)
                assertEquals(401, records().last().arguments[5])
                assertNull(records().last().arguments[0])
                for (path in listOf("/api/jobs", "/api/mcp")) {
                    val beforeFailure = records().size
                    assertEquals(500, request(prefix + path, tokens.issue(99, 0)).statusCode())
                    assertEquals(beforeFailure + 1, records().size)
                    assertEquals(500, records().last().arguments[5])
                    assertNull(records().last().arguments[0])
                }
                val before = records().size
                assertEquals(200, request("$prefix/api/health").statusCode())
                assertEquals(200, request("$prefix/doc.html").statusCode())
                assertEquals(before, records().size)
            }
        } finally {
            try {
                server.stop()
                server.destroy()
            } finally {
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }
}
