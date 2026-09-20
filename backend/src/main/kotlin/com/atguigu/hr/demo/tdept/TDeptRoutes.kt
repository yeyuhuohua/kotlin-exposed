package com.atguigu.hr.demo.tdept

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
fun Route.tDeptRoutes() {
    /** t_dept 门派示例表。 */
    get("/t-dept") {
        val cached = TDeptService.listTDept()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "t_dept 门派示例表"
        tag("t-dept")
        responseExamples(listOf(sampleTDept))
    }

    /** 新增门派并刷新缓存。id 由数据库自增。 */
    post("/t-dept") {
        val body = call.receive<TDeptCreateRequest>()
        if (!body.hasValues()) {
            return@post call.respondFail(HttpStatusCode.BadRequest, "deptName or address is required")
        }
        val created = TDeptService.createTDept(body)
        call.respondOk(created, message = "created")
    }.describe {
        summary = "新增门派并刷新缓存"
        tag("t-dept")
        requestExample(sampleTDeptCreate, description = "id 自增；至少填 deptName 或 address")
        responseExamples(
            sampleTDept.copy(id = 7, deptName = "恒山", address = "大同"),
            message = "created",
            okDescription = "创建成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "deptName or address is required",
                HttpStatusCode.Conflict to "create failed",
            ),
        )
    }

    /** 部分更新门派并刷新缓存。 */
    put("/t-dept/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid t_dept id")
        val patch = call.receive<TDeptUpdateRequest>()
        if (!patch.hasUpdates()) {
            return@put call.respondFail(HttpStatusCode.BadRequest, "no fields to update")
        }
        val updated = TDeptService.updateTDept(id, patch)
            ?: return@put call.respondFail(HttpStatusCode.NotFound, "t_dept not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "部分更新门派并刷新缓存"
        tag("t-dept")
        parameters {
            path("id") { description = "t_dept 主键，例如 1" }
        }
        requestExample(sampleTDeptPatch)
        responseExamples(
            sampleTDept,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "no fields to update",
                HttpStatusCode.NotFound to "t_dept not found",
                HttpStatusCode.Conflict to "update failed",
            ),
        )
    }
}
