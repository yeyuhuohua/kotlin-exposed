package com.atguigu.hr.auth

import com.atguigu.hr.audit.AuditService
import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.common.api.respondFail
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.requestExample
import com.atguigu.hr.docs.responseExamples
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.userAgent
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import java.util.Locale

/** 注册认证、用户和角色管理接口，管理路由统一启用 ADMIN 限制。 */

@OptIn(ExperimentalKtorApi::class)
fun Route.authPublicRoutes(service: AuthService, throttle: LoginThrottle) {
    post("/auth/login") {
        call.response.header("Cache-Control", "no-store")
        val body = call.receive<LoginRequest>()
        val remoteHost = call.request.origin.remoteHost
        val auditIp = AuditService.clientIp(call)
        val auditUserAgent = call.request.userAgent()
        // 记录用与登录校验相同的归一化形式，但不做合法性校验（非法用户名也要留痕）。
        val auditUsername = body.username.trim().lowercase(Locale.ROOT).take(64)
        throttle.blockedSeconds(remoteHost, body.username)?.let { retryAfter ->
            AuditService.recordLogin(auditUsername, null, auditIp, auditUserAgent, success = false, errorCode = "rate_limited")
            call.response.header(HttpHeaders.RetryAfter, retryAfter.toString())
            return@post call.respondFail(
                HttpStatusCode.TooManyRequests,
                "too many login attempts, retry later",
            )
        }
        try {
            val token = service.login(body)
            throttle.recordSuccess(body.username)
            AuditService.recordLogin(auditUsername, token.user.id, auditIp, auditUserAgent, success = true, errorCode = null)
            call.respondOk(token)
        } catch (cause: AuthException) {
            throttle.recordFailure(remoteHost, body.username)
            AuditService.recordLogin(auditUsername, null, auditIp, auditUserAgent, success = false, errorCode = "invalid_credentials")
            throw cause
        }
    }.describe {
        summary = "使用数据库账号登录并获取访问 Token"
        tag("auth")
        requestExample(sampleLogin, "用户名和密码")
        responseExamples(
            sampleToken,
            fails = arrayOf(
                HttpStatusCode.Unauthorized to "invalid username or password",
                HttpStatusCode.TooManyRequests to "too many login attempts, retry later",
            ),
        )
    }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.authProtectedRoutes(service: AuthService) {
    get("/auth/me") {
        call.response.header("Cache-Control", "no-store")
        call.respondOk(call.authUser().toCurrentUser())
    }.describe {
        summary = "查询当前用户、角色及有效权限"
        tag("auth")
        responseExamples(sampleCurrentUser)
    }
    post("/auth/logout") {
        service.logout(call.authUser().id)
        call.respondOk("logged out on all devices")
    }.describe {
        summary = "退出登录并撤销当前账号在所有设备上的 Token"
        tag("auth")
        responseExamples("logged out on all devices")
    }

    route("/auth/users") {
        install(RoleAuthorization) { adminOnly = true }
        get {
            call.response.header("Cache-Control", "no-store")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
            call.respondOk(service.listUsers(limit, offset))
        }.describe {
            summary = "分页查询用户（仅 ADMIN）"
            tag("auth")
            parameters {
                query("limit") { description = "每页条数，默认 50，最大 200" }
                query("offset") { description = "跳过条数，默认 0" }
            }
            responseExamples(ApiList(total = 1, items = listOf(sampleUser)))
        }
        post {
            call.respondOk(service.createUser(call.receive<UserCreateRequest>()), message = "created")
        }.describe {
            summary = "创建用户（仅 ADMIN；不提供公开注册）"
            tag("auth")
            requestExample(sampleUserCreate, "密码长度为 8-128 个字符；roleCode 必须是已启用的角色编码")
            responseExamples(sampleUser.copy(id = 2, username = "reader", roleCode = "READER"), message = "created",
                fails = arrayOf(HttpStatusCode.BadRequest to "invalid user", HttpStatusCode.Conflict to "username already exists"))
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
                ?: throw AuthException(HttpStatusCode.BadRequest, "invalid user id")
            call.respondOk(service.updateUser(call.authUser().id, id, call.receive<UserUpdateRequest>()), message = "updated")
        }.describe {
            summary = "调整角色、启停账号或重置密码，并撤销已有 Token（仅 ADMIN）"
            tag("auth")
            parameters { path("id") { description = "用户编号" } }
            requestExample(sampleUserUpdate)
            responseExamples(sampleUser.copy(enabled = false), message = "updated",
                fails = arrayOf(HttpStatusCode.BadRequest to "invalid update", HttpStatusCode.NotFound to "user not found"))
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
                ?: throw AuthException(HttpStatusCode.BadRequest, "invalid user id")
            if (!service.deleteUser(call.authUser().id, id)) {
                throw AuthException(HttpStatusCode.NotFound, "user not found")
            }
            call.respondOk("deleted")
        }.describe {
            summary = "删除账号（仅 ADMIN；admin 账号与当前登录账号不可删除）"
            tag("auth")
            parameters { path("id") { description = "用户编号" } }
            responseExamples("deleted", fails = arrayOf(
                HttpStatusCode.BadRequest to "cannot delete your own account",
                HttpStatusCode.Forbidden to "admin account is protected",
                HttpStatusCode.NotFound to "user not found",
            ))
        }
    }
    route("/auth/roles") {
        install(RoleAuthorization) { adminOnly = true }
        post {
            call.respondOk(service.createRole(call.receive<RoleCreateRequest>()), message = "created")
        }.describe {
            summary = "创建角色（仅 ADMIN；初始业务权限为空）"
            tag("auth")
            requestExample(RoleCreateRequest("HR_VIEWER", "人事查询员"), "角色编码为 2-20 位大写字母、数字或下划线，以字母开头")
            responseExamples(RoleDto("HR_VIEWER", "人事查询员", true), message = "created",
                fails = arrayOf(HttpStatusCode.Conflict to "role already exists"))
        }
        put("/{code}") {
            call.respondOk(service.updateRole(call.roleCode(), call.receive<RoleUpdateRequest>()), message = "updated")
        }.describe {
            summary = "修改角色名称或状态（仅 ADMIN；ADMIN 角色受保护）"
            tag("auth")
            parameters { path("code") { description = "角色编码" } }
            requestExample(RoleUpdateRequest(name = "人事查询员"))
            responseExamples(RoleDto("HR_VIEWER", "人事查询员", true), message = "updated",
                fails = arrayOf(HttpStatusCode.Forbidden to "ADMIN role is protected", HttpStatusCode.NotFound to "role not found"))
        }
        get("/{code}/permissions") {
            call.response.header("Cache-Control", "no-store")
            call.respondOk(service.getRolePermissions(call.authUser(), call.roleCode()))
        }.describe {
            summary = "查看角色的页面与接口权限（仅 ADMIN）"
            tag("auth")
            parameters { path("code") { description = "角色编码" } }
            responseExamples(samplePermissions, fails = arrayOf(HttpStatusCode.NotFound to "role not found"))
        }
        put("/{code}/permissions") {
            call.respondOk(service.updateRolePermissions(call.authUser(), call.roleCode(), call.receive<RolePermissionsRequest>()))
        }.describe {
            summary = "替换角色权限并撤销该角色所有用户的 Token（仅 ADMIN）"
            tag("auth")
            parameters { path("code") { description = "角色编码" } }
            requestExample(RolePermissionsRequest(0, samplePermissions.permissions), "使用 GET 返回的版本号；空集合表示拒绝全部可配置访问；ADMIN 角色不可修改")
            responseExamples(samplePermissions, fails = arrayOf(
                HttpStatusCode.BadRequest to "unknown permission code",
                HttpStatusCode.Forbidden to "ADMIN role is protected",
                HttpStatusCode.Conflict to "permissions changed; reload before saving",
            ))
        }
        delete("/{code}") {
            if (!service.deleteRole(call.roleCode())) {
                throw AuthException(HttpStatusCode.NotFound, "role not found")
            }
            call.respondOk("deleted")
        }.describe {
            summary = "删除角色（仅 ADMIN；ADMIN 角色受保护，仍有账号引用时返回 409）"
            tag("auth")
            parameters { path("code") { description = "角色编码" } }
            responseExamples("deleted", fails = arrayOf(
                HttpStatusCode.Forbidden to "ADMIN role is protected",
                HttpStatusCode.NotFound to "role not found",
                HttpStatusCode.Conflict to "role still has 2 user(s)",
            ))
        }
        get {
            call.respondOk(service.listRoles())
        }.describe {
            summary = "查询角色列表（仅 ADMIN）"
            tag("auth")
            responseExamples(listOf(RoleDto("ADMIN", "Administrator", true), RoleDto("READER", "Read only", true)))
        }
    }
    route("/auth/permissions") {
        install(RoleAuthorization) { adminOnly = true }
        get {
            call.response.header("Cache-Control", "no-store")
            call.respondOk(PermissionCatalog.definitions)
        }.describe {
            summary = "查询页面及按 HTTP 方法、路径划分的接口权限目录（仅 ADMIN）"
            tag("auth")
            responseExamples(PermissionCatalog.definitions)
        }
    }
}

private fun ApplicationCall.authUser(): AuthUser = checkNotNull(principal<UserPrincipal>()).user
private fun ApplicationCall.roleCode(): String = parameters["code"]?.takeIf { Regex("[A-Z][A-Z0-9_]{1,19}").matches(it) }
    ?: throw AuthException(HttpStatusCode.BadRequest, "invalid role code")
