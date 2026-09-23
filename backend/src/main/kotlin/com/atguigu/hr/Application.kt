package com.atguigu.hr

import com.atguigu.hr.audit.ApiCallAudit
import com.atguigu.hr.audit.AuditRepository
import com.atguigu.hr.audit.AuditService
import com.atguigu.hr.audit.auditRoutes
import com.atguigu.hr.auth.AuthSettings
import com.atguigu.hr.auth.LoginThrottle
import com.atguigu.hr.auth.TokenService
import com.atguigu.hr.auth.authErrors
import com.atguigu.hr.auth.authProtectedRoutes
import com.atguigu.hr.auth.authPublicRoutes
import com.atguigu.hr.auth.createAuthService
import com.atguigu.hr.auth.installTokenAuthentication
import com.atguigu.hr.auth.withBusinessPermissions
import com.atguigu.hr.common.api.respondFail
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
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.auth.authenticate
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException
import io.ktor.server.routing.route
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID

/** 由 application.yaml 启动 Netty，入口类为 io.ktor.server.netty.EngineMain。 */
fun main(args: Array<String>) = EngineMain.main(args)

/** 安装插件并挂载路由：文档页、Swagger、HR 查询接口。 */
fun Application.module() {
    val tokens = TokenService(AuthSettings.from(environment.config))
    DatabaseFactory.connect(environment.config)
    // Complete schema/bootstrap work before accepting authenticated requests.
    val authService = runBlocking { createAuthService(environment.config, DatabaseFactory.database, tokens) }
    if (environment.config.propertyOrNull("auth.initializeSchema")?.getString()?.toBooleanStrict() != false) {
        runBlocking { AuditRepository.initialize() }
    }
    installTokenAuthentication(authService, tokens)
    RedisFactory.connect(environment.config)
    val loginThrottle = loginThrottle(environment.config)
    // 进程退出时关掉 Lettuce 连接和 client
    monitor.subscribe(ApplicationStopped) {
        RedisFactory.shutdown()
        AuditService.shutdown()
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
        // 关联同一次请求的所有日志；值由 CallId 插件生成或从 X-Request-Id 透传。
        mdc("requestId") { call -> call.callId }
    }
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString().replace("-", "").take(10) }
        replyToHeader(HttpHeaders.XRequestId)
    }
    install(createApplicationPlugin("ApiSecurityHeaders") {
        onCall { call ->
            if (call.request.path().startsWith("/api")) {
                call.response.headers.append("X-Content-Type-Options", "nosniff")
                call.response.headers.append("X-Frame-Options", "DENY")
                call.response.headers.append("Referrer-Policy", "no-referrer")
            }
        }
    })
    // 默认只允许本机开发来源；跨域部署时在 application.yaml 的 cors.allowedHosts 里显式列出。
    val corsHosts = environment.config.propertyOrNull("cors.allowedHosts")?.getList().orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
    val corsAnyHost = corsHosts.contains("*")
    if (corsAnyHost) {
        environment.log.warn("CORS: cors.allowedHosts 包含 *，任何来源都可调用接口，请勿用于生产环境")
    }
    install(CORS) {
        when {
            corsAnyHost -> anyHost()
            // 显式配置按原样匹配；Ktor 的 allowHost 只匹配默认端口，带端口要写成 host:port
            corsHosts.isNotEmpty() -> corsHosts.forEach { host -> allowHost(host) }
            // 未配置时放行本机任意端口：Vite 开发端口可能变化（5173 被占用时会顺延）
            else -> allowOrigins(::isLocalOrigin)
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    install(StatusPages) {
        authErrors()
        exception<UnsupportedMediaTypeException> { call, cause ->
            call.application.environment.log.warn("Unsupported media type", cause)
            call.respondFail(HttpStatusCode.UnsupportedMediaType, "unsupported media type")
        }
        exception<CannotTransformContentToTypeException> { call, cause ->
            call.application.environment.log.warn("Cannot transform request body", cause)
            call.respondFail(HttpStatusCode.UnsupportedMediaType, "unsupported media type")
        }
        exception<BadRequestException> { call, cause ->
            // 请求体解析失败属于调用方问题，回显细节便于排错。
            val detail = cause.cause?.message ?: cause.message ?: "invalid request body"
            call.respondFail(HttpStatusCode.BadRequest, detail)
        }
        exception<SerializationException> { call, cause ->
            call.respondFail(HttpStatusCode.BadRequest, cause.message ?: "invalid request body")
        }
        exception<CancellationException> { _, cause ->
            throw cause
        }
        exception<Throwable> { call, cause ->
            if (cause.isConstraintConflict()) {
                // 数据库约束细节只进日志，不回显给调用方。
                call.application.environment.log.warn("Constraint conflict", cause)
                call.respondFail(HttpStatusCode.Conflict, "resource conflict")
                return@exception
            }
            call.application.environment.log.error("Unhandled error", cause)
            call.respondFail(HttpStatusCode.InternalServerError, "internal error")
        }
    }

    routing {
        docsRoutes()
        route("/api") {
            install(ApiCallAudit)
            healthRoutes()
            authPublicRoutes(authService, loginThrottle)
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
    auditRoutes()
}

/** 本机开发来源：http(s)://localhost、127.0.0.1 或 ::1，端口不限。 */
internal fun isLocalOrigin(origin: String): Boolean {
    if (!origin.startsWith("http://") && !origin.startsWith("https://")) return false
    val authority = origin.substringAfter("://").substringBefore('/')
    if (authority.isEmpty()) return false
    val host = if (authority.startsWith("[")) {
        authority.substringAfter('[').substringBefore(']')
    } else {
        authority.substringBefore(':')
    }
    return host == "localhost" || host == "127.0.0.1" || host == "::1"
}

/** 登录限流参数来自 application.yaml 的 auth.loginRateLimit，缺省为开启。 */
private fun loginThrottle(config: ApplicationConfig): LoginThrottle {
    val section = "auth.loginRateLimit"
    return LoginThrottle.of(
        enabled = config.propertyOrNull("$section.enabled")?.getString()?.toBooleanStrictOrNull() ?: true,
        windowSeconds = config.propertyOrNull("$section.windowSeconds")?.getString()?.toLongOrNull() ?: 300,
        maxAccountFailures = config.propertyOrNull("$section.maxAccountFailures")?.getString()?.toIntOrNull() ?: 8,
        maxAddressFailures = config.propertyOrNull("$section.maxAddressFailures")?.getString()?.toIntOrNull() ?: 30,
    )
}
