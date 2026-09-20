package com.atguigu.hr.docs

import com.atguigu.hr.common.api.ApiResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.Operation

/** 一次写入全部 responses。Ktor 的 responses {} 是整段替换，不能多次调用。 */
internal inline fun <reified T : Any> Operation.Builder.responseExamples(
    data: T,
    message: String = "ok",
    okDescription: String = "成功",
    vararg fails: Pair<HttpStatusCode, String>,
) {
    responses {
        HttpStatusCode.OK {
            description = okDescription
            ContentType.Application.Json {
                example(
                    "ok",
                    ExampleObject(
                        summary = okDescription,
                        value = GenericElement(ApiResult.ok(data, message)),
                    ),
                )
            }
        }
        fails.forEach { (status, errorMessage) ->
            status {
                description = errorMessage
                ContentType.Application.Json {
                    example(
                        "error",
                        ExampleObject(
                            summary = errorMessage,
                            value = GenericElement(ApiResult.fail(status.value, errorMessage)),
                        ),
                    )
                }
            }
        }
    }
}

/** Knife4j 调试页需要 requestBody + example，否则请求体是空的。 */
internal inline fun <reified T : Any> Operation.Builder.requestExample(
    body: T,
    description: String = "只提交要改的字段",
) {
    requestBody {
        this.description = description
        required = true
        ContentType.Application.Json {
            example(
                "body",
                ExampleObject(
                    summary = description,
                    value = GenericElement(body),
                ),
            )
        }
    }
}
