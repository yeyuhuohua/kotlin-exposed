package com.atguigu.hr.docs

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Server
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable

private val apiInfo = OpenApiInfo(
    title = "atguigudb HR API",
    version = "0.0.1",
    description = "由 Ktor 官方从路由生成 OpenAPI。GET 走 Redis 缓存，统一返回 { code, message, data }。",
)

@Serializable
private data class Knife4jGroup(
    val name: String,
    val url: String,
    val swaggerVersion: String = "3.0",
    val location: String,
)

@Serializable
private data class SwaggerUiConfig(
    val configUrl: String = "/v3/api-docs/swagger-config",
    val url: String = "/v3/api-docs",
    val urls: List<SwaggerUiUrl> = listOf(SwaggerUiUrl()),
)

@Serializable
private data class SwaggerUiUrl(
    val name: String = "default",
    val url: String = "/v3/api-docs",
)

/** 文档 UI 全部来自本地 jar，不访问 CDN。默认 Knife4j。 */
@OptIn(ExperimentalKtorApi::class)
fun Route.docsRoutes() {
    get("/") { call.respondRedirect("/doc.html") }.hide()
    get("/docs") { call.respondRedirect("/doc.html") }.hide()
    get("/scalar") { call.respondRedirect("/doc.html") }.hide()

    get("/doc.html") {
        val html = javaClass.classLoader.getResource("META-INF/resources/doc.html")?.readBytes()
            ?: return@get call.respondText("Knife4j UI missing", status = HttpStatusCode.InternalServerError)
        call.respondBytes(html, ContentType.Text.Html)
    }.hide()

    get("/swagger") {
        val html = javaClass.classLoader.getResource("static/swagger.html")?.readBytes()
            ?: return@get call.respondText("Swagger UI missing", status = HttpStatusCode.InternalServerError)
        call.respondBytes(html, ContentType.Text.Html)
    }.hide()

    staticResources("/webjars", "META-INF/resources/webjars").hide()

    get("/v3/api-docs") {
        val doc = OpenApiDoc(
            openapi = "3.0.3",
            info = apiInfo,
            servers = listOf(Server(url = "/")),
        ) + call.application.routingRoot.descendants()
        call.respondText(doc.toKnife4jJson(), ContentType.Application.Json)
    }.hide()

    get("/v3/api-docs/swagger-config") {
        call.respond(SwaggerUiConfig())
    }.hide()

    get("/swagger-resources") {
        call.respond(
            listOf(
                Knife4jGroup(
                    name = "default",
                    url = "/v3/api-docs",
                    location = "/v3/api-docs",
                ),
            ),
        )
    }.hide()

    get("/swagger-resources/configuration/ui") {
        call.respond(mapOf("deepLinking" to true, "displayOperationId" to false))
    }.hide()

    get("/swagger-resources/configuration/security") {
        call.respond(mapOf<String, String>())
    }.hide()
}
