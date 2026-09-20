package com.atguigu.hr.docs

import io.ktor.openapi.OpenApiDoc
import com.atguigu.hr.auth.withAuthDocumentation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Knife4j 4.x 解析不了 OpenAPI 3.1，且不能带 null 字段、operation 必须有 responses。 */
private val knife4jJson = Json {
    prettyPrint = true
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

private val httpOps = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

fun OpenApiDoc.toKnife4jJson(): String {
    val raw = knife4jJson.encodeToJsonElement(OpenApiDoc.serializer(), copy(openapi = "3.0.3"))
    val cleaned = stripNulls(raw)
    val withResponses = ensureOperationResponses(cleaned)
    val knife4jReady = adaptResponseExamplesForKnife4j(withResponses)
    return knife4jJson.encodeToString(JsonElement.serializer(), withAuthDocumentation(knife4jReady))
}

private fun stripNulls(element: JsonElement): JsonElement = when (element) {
    is JsonNull -> element
    is JsonArray -> JsonArray(element.map(::stripNulls).filter { it !is JsonNull })
    is JsonObject -> JsonObject(
        element.mapNotNull { (k, v) ->
            val next = stripNulls(v)
            if (next is JsonNull) null else k to next
        }.toMap(),
    )
    else -> element
}

private fun ensureOperationResponses(element: JsonElement): JsonElement {
    val root = element as? JsonObject ?: return element
    val paths = root["paths"] as? JsonObject ?: return root
    val defaultResponses = JsonObject(
        mapOf(
            "200" to JsonObject(mapOf("description" to JsonPrimitive("OK"))),
        ),
    )
    val newPaths = JsonObject(
        paths.mapValues { (_, pathItem) ->
            val obj = pathItem as? JsonObject ?: return@mapValues pathItem
            JsonObject(
                obj.mapValues { (method, op) ->
                    if (method !in httpOps) return@mapValues op
                    val opObj = op as? JsonObject ?: return@mapValues op
                    if (opObj.containsKey("responses")) opObj
                    else JsonObject(opObj + ("responses" to defaultResponses))
                },
            )
        },
    )
    return JsonObject(root + ("paths" to newPaths) + ("openapi" to JsonPrimitive("3.0.3")))
}

/**
 * Knife4j 4.5 文档页 ACE 只要字符串。OAS3 examples 映射会被收成对象，响应示例就是空的。
 * 保留已有 schema（required、描述等），只把示例写成单数 example；没有 schema 时才从示例推导。
 */
private fun adaptResponseExamplesForKnife4j(element: JsonElement): JsonElement {
    val root = element as? JsonObject ?: return element
    val paths = root["paths"] as? JsonObject ?: return root
    val newPaths = JsonObject(
        paths.mapValues { (_, pathItem) ->
            val obj = pathItem as? JsonObject ?: return@mapValues pathItem
            JsonObject(
                obj.mapValues { (method, op) ->
                    if (method !in httpOps) return@mapValues op
                    val opObj = op as? JsonObject ?: return@mapValues op
                    var nextOp = opObj
                    val responses = nextOp["responses"] as? JsonObject
                    if (responses != null) {
                        nextOp = JsonObject(
                            nextOp + (
                                "responses" to JsonObject(
                                    responses.mapValues { (_, resp) -> adaptResponseMedia(resp) },
                                )
                                ),
                        )
                    }
                    val requestBody = nextOp["requestBody"] as? JsonObject
                    if (requestBody != null) {
                        nextOp = JsonObject(nextOp + ("requestBody" to adaptResponseMedia(requestBody)))
                    }
                    nextOp
                },
            )
        },
    )
    return JsonObject(root + ("paths" to newPaths))
}

private fun adaptResponseMedia(resp: JsonElement): JsonElement {
    val obj = resp as? JsonObject ?: return resp
    val content = obj["content"] as? JsonObject ?: return obj
    val newContent = JsonObject(
        content.mapValues { (_, media) -> rewriteMediaTypeForKnife4j(media) },
    )
    return JsonObject(obj + ("content" to newContent))
}

private fun rewriteMediaTypeForKnife4j(media: JsonElement): JsonElement {
    val obj = media as? JsonObject ?: return media
    val firstValue = firstExampleValue(obj)
    val next = obj.toMutableMap()
    if (firstValue != null) {
        next["example"] = firstValue
        next.remove("examples")
    }
    val schema = next["schema"]
    when {
        schema == null && firstValue != null -> next["schema"] = schemaFromExample(firstValue)
        schema is JsonObject && "\$ref" !in schema && "example" !in schema && firstValue != null ->
            next["schema"] = JsonObject(schema + ("example" to firstValue))
    }
    return JsonObject(next)
}

private fun firstExampleValue(media: JsonObject): JsonElement? {
    val examples = media["examples"] as? JsonObject
    val fromMap = examples?.values
        ?.filterIsInstance<JsonObject>()
        ?.mapNotNull { it["value"] }
        ?.firstOrNull()
    return fromMap ?: media["example"]
}

private fun schemaFromExample(value: JsonElement): JsonObject = when (value) {
    is JsonObject -> JsonObject(
        mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(value.mapValues { (_, v) -> schemaFromExample(v) }),
            "example" to value,
        ),
    )
    is JsonArray -> JsonObject(
        buildMap {
            put("type", JsonPrimitive("array"))
            put("items", schemaFromExample(value.firstOrNull() ?: JsonObject(emptyMap())))
            put("example", value)
        },
    )
    is JsonNull -> JsonObject(
        mapOf(
            "nullable" to JsonPrimitive(true),
        ),
    )
    is JsonPrimitive -> JsonObject(
        mapOf(
            "type" to JsonPrimitive(primitiveType(value)),
            "example" to value,
        ),
    )
}

private fun primitiveType(value: JsonPrimitive): String = when {
    value.isString -> "string"
    value.booleanOrNull != null -> "boolean"
    value.longOrNull != null && '.' !in value.content -> "integer"
    value.doubleOrNull != null -> "number"
    else -> "string"
}
