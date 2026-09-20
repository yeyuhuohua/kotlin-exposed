package com.atguigu.hr

import com.atguigu.hr.auth.AuthSettings
import com.atguigu.hr.auth.TokenService
import com.atguigu.hr.auth.authErrors
import com.atguigu.hr.auth.authProtectedRoutes
import com.atguigu.hr.auth.authPublicRoutes
import com.atguigu.hr.auth.createAuthService
import com.atguigu.hr.auth.installTokenAuthentication
import com.atguigu.hr.auth.withBusinessPermissions
import com.atguigu.hr.common.api.ApiResult
import com.atguigu.hr.common.database.isConstraintConflict
import com.atguigu.hr.config.DatabaseFactory
import com.atguigu.hr.config.RedisFactory
import com.atguigu.hr.demo.order.orderRoutes
import com.atguigu.hr.demo.tdept.tDeptRoutes
import com.atguigu.hr.demo.temp.tEmpRoutes
import com.atguigu.hr.department.departmentRoutes
import com.atguigu.hr.docs.docsRoutes
import com.atguigu.hr.employee.employeeRoutes
import com.atguigu.hr.geography.geographyRoutes
import com.atguigu.hr.health.healthRoutes
import com.atguigu.hr.job.jobRoutes
import com.atguigu.hr.jobgrade.jobGradeRoutes
import com.atguigu.hr.jobhistory.jobHistoryRoutes
import com.atguigu.hr.location.locationRoutes
import com.atguigu.hr.overview.overviewRoutes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.server.routing.route
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/** 由 application.yaml 启动 Netty，入口类为 io.ktor.server.netty.EngineMain。 */
fun main(args: Array<String>) = EngineMain.main(args)

/** 安装插件并挂载路由：文档页、Swagger、HR 查询接口。 */
fun Application.module() {
    val tokens = TokenService(AuthSettings.from(environment.config))
    DatabaseFactory.connect(environment.config)
    // Complete schema/bootstrap work before accepting authenticated requests.
    val authService = runBlocking { createAuthService(environment.config, DatabaseFactory.database, tokens) }
    installTokenAuthentication(authService, tokens)
    RedisFactory.connect(environment.config)
    // 进程退出时关掉 Lettuce 连接和 client
    monitor.subscribe(ApplicationStopped) {
        RedisFactory.shutdown()
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        authErrors()
        exception<UnsupportedMediaTypeException> { call, cause ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ApiResult.fail(415, cause.message ?: "unsupported media type"),
            )
        }
        exception<CannotTransformContentToTypeException> { call, cause ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                ApiResult.fail(415, cause.message ?: "unsupported media type"),
            )
        }
        exception<BadRequestException> { call, cause ->
            val detail = cause.cause?.message ?: cause.message ?: "invalid request body"
            call.respond(HttpStatusCode.BadRequest, ApiResult.fail(400, detail))
        }
        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiResult.fail(400, cause.message ?: "invalid request body"),
            )
        }
        exception<CancellationException> { _, cause ->
            throw cause
        }
        exception<Throwable> { call, cause ->
            if (cause.isConstraintConflict()) {
                call.respond(
                    HttpStatusCode.Conflict,
                    ApiResult.fail(409, cause.message ?: "conflict"),
                )
                return@exception
            }
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResult.fail(500, cause.message ?: cause::class.simpleName ?: "internal error"),
            )
        }
    }

    routing {
        docsRoutes()
        route("/api") {
            healthRoutes()
            authPublicRoutes(authService)
            authenticate("auth-jwt") {
                authProtectedRoutes(authService)
                withBusinessPermissions { businessRoutes() }
            }
        }
    }

    monitor.subscribe(ApplicationStarted) {
        val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
        val bindHost = environment.config.propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
        val browseHost = if (bindHost == "0.0.0.0" || bindHost == "::") "localhost" else bindHost
        val base = "http://$browseHost:$port"
        environment.log.info(
            """
            |
            |  API 文档:
            |    Knife4j  $base/doc.html
            |    Swagger  $base/swagger
            |    OpenAPI  $base/v3/api-docs
            """.trimMargin(),
        )
    }
}

internal fun Route.businessRoutes() {
    overviewRoutes()
    employeeRoutes()
    departmentRoutes()
    jobRoutes()
    locationRoutes()
    geographyRoutes()
    jobHistoryRoutes()
    jobGradeRoutes()
    tDeptRoutes()
    tEmpRoutes()
    orderRoutes()
}
