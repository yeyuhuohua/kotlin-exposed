package com.atguigu.hr.auth

import com.atguigu.hr.common.api.respondFail
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

data class UserPrincipal(val user: AuthUser)

fun Application.installTokenAuthentication(service: AuthService, tokens: TokenService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "hr-api"
            verifier(tokens.verifier)
            validate { credential ->
                val id = credential.payload.subject?.toIntOrNull() ?: return@validate null
                val version = runCatching { credential.payload.getClaim("ver").asInt() }.getOrNull()
                    ?: return@validate null
                service.authenticate(id, version)?.let(::UserPrincipal)
            }
            challenge { _, _ ->
                call.response.headers.append("WWW-Authenticate", "Bearer realm=\"hr-api\"")
                call.respondFail(HttpStatusCode.Unauthorized, "missing, invalid or expired token")
            }
        }
    }
}

class RoleAuthorizationConfig {
    var adminOnly: Boolean = false
}

val RoleAuthorization = createRouteScopedPlugin("RoleAuthorization", ::RoleAuthorizationConfig) {
    val adminOnly = pluginConfig.adminOnly
    on(AuthenticationChecked) { call ->
        val user = call.principal<UserPrincipal>()?.user
        if (user == null) {
            call.respondFail(HttpStatusCode.Unauthorized, "authentication required")
            return@on
        }
        val permission = PermissionCatalog.requiredApi(call.request.httpMethod.value, call.request.path())
        val allowed = permission != null && user.hasPermission(permission.code) &&
            (!adminOnly || user.roleCode == RoleCode.ADMIN.name)
        if (!allowed) call.respondFail(HttpStatusCode.Forbidden, "insufficient permissions")
    }
}

// An empty path reuses its parent in Ktor; use a distinct transparent node so
// business write restrictions do not leak into /auth/logout or /auth/me.
fun Route.withBusinessPermissions(build: Route.() -> Unit) {
    val child = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent
    })
    child.install(RoleAuthorization)
    child.build()
}

fun StatusPagesConfig.authErrors() {
    exception<AuthException> { call, cause ->
        if (cause.status == HttpStatusCode.Unauthorized) {
            call.response.headers.append("WWW-Authenticate", "Bearer realm=\"hr-api\"")
        }
        call.respondFail(cause.status, cause.message, cause.error)
    }
}
