package com.atguigu.hr.auth

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 向动态 OpenAPI 补充 Bearer 认证及接口权限说明，不改变业务接口的实际返回值。 */

internal fun withAuthDocumentation(element: JsonElement): JsonElement {
    val root = element as? JsonObject ?: return element
    val components = root["components"] as? JsonObject ?: JsonObject(emptyMap())
    val schemes = components["securitySchemes"] as? JsonObject ?: JsonObject(emptyMap())
    val bearer = JsonObject(mapOf("type" to JsonPrimitive("http"), "scheme" to JsonPrimitive("bearer"), "bearerFormat" to JsonPrimitive("JWT")))
    val requirement = JsonArray(listOf(JsonObject(mapOf("bearerAuth" to JsonArray(emptyList())))))
    val paths = root["paths"] as? JsonObject ?: JsonObject(emptyMap())
    val securedPaths = JsonObject(paths.mapValues { (path, item) ->
        val pathItem = item as? JsonObject ?: return@mapValues item
        JsonObject(pathItem.mapValues operation@{ (method, value) ->
            if (method !in setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")) return@operation value
            val operation = value as? JsonObject ?: return@operation value
            val public = path == "/api/health" || path == "/api/auth/login" || !path.startsWith("/api/")
            if (public) return@operation JsonObject(operation + ("security" to JsonArray(emptyList())))
            val permission = PermissionCatalog.requiredApi(method.uppercase(), path)
            val adminOnly = permission?.adminOnly == true
            val responses = operation["responses"] as? JsonObject ?: JsonObject(emptyMap())
            val errors = mapOf(
                "401" to JsonObject(mapOf("description" to JsonPrimitive("Token 缺失、无效、过期或已撤销，或者用户、角色已停用"))),
                "403" to JsonObject(mapOf("description" to JsonPrimitive("缺少接口权限或所需的 ADMIN 角色"))),
            )
            JsonObject(operation + mapOf(
                "security" to requirement,
                "x-required-roles" to JsonArray((if (adminOnly) listOf("ADMIN") else listOf("ADMIN", "READER")).map(::JsonPrimitive)),
                "x-permission" to JsonPrimitive(permission?.code ?: "authenticated"),
                "responses" to JsonObject(errors + responses),
            ))
        })
    })
    return JsonObject(root + mapOf(
        "components" to JsonObject(components + ("securitySchemes" to JsonObject(schemes + ("bearerAuth" to bearer)))),
        "paths" to securedPaths,
    ))
}
