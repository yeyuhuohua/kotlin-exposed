package com.atguigu.hr.demo.temp

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondFail
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.requestExample
import com.atguigu.hr.docs.responseExamples
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.tEmpRoutes() {
    /** t_emp 人物示例表。 */
    get("/t-emp") {
        val cached = TEmpService.listTEmp()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "t_emp 人物示例表"
        tag("t-emp")
        responseExamples(listOf(sampleTEmp))
    }

    /** 新增人物并刷新缓存。id 由数据库自增。 */
    post("/t-emp") {
        val body = call.receive<TEmpCreateRequest>()
        val created = TEmpService.createTEmp(body)
        call.respondOk(created, message = "created")
    }.describe {
        summary = "新增人物并刷新缓存"
        tag("t-emp")
        requestExample(sampleTEmpCreate, description = "id 自增；empno 必填")
        responseExamples(
            sampleTEmp.copy(id = 11, name = "仪琳", age = 18, empno = 2001),
            message = "created",
            okDescription = "创建成功",
            fails = arrayOf(
                HttpStatusCode.Conflict to "create failed",
            ),
        )
    }

    /** 部分更新人物并刷新缓存。 */
    put("/t-emp/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid t_emp id")
        val patch = call.receive<TEmpUpdateRequest>()
        if (!patch.hasUpdates()) {
            return@put call.respondFail(HttpStatusCode.BadRequest, "no fields to update")
        }
        val updated = TEmpService.updateTEmp(id, patch)
            ?: return@put call.respondFail(HttpStatusCode.NotFound, "t_emp not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "部分更新人物并刷新缓存"
        tag("t-emp")
        parameters {
            path("id") { description = "t_emp 主键，例如 1" }
        }
        requestExample(sampleTEmpPatch)
        responseExamples(
            sampleTEmp,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "no fields to update",
                HttpStatusCode.NotFound to "t_emp not found",
                HttpStatusCode.Conflict to "update failed",
            ),
        )
    }
}
