package com.atguigu.hr.employee

import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondFail
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.requestExample
import com.atguigu.hr.docs.responseExamples
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

@OptIn(ExperimentalKtorApi::class)
fun Route.employeeRoutes() {
    /** 分页查询员工，支持部门、岗位、关键字筛选。 */
    get("/employees") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val departmentId = call.request.queryParameters["departmentId"]?.toIntOrNull()
        val jobId = call.request.queryParameters["jobId"]
        val q = call.request.queryParameters["q"]
        val cached = EmployeeService.listEmployees(limit, offset, departmentId, jobId, q)
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "分页查询员工"
        tag("employees")
        parameters {
            query("limit") { description = "每页条数，默认 50，最大 200" }
            query("offset") { description = "跳过条数，默认 0。limit=50&offset=50 为第二页" }
            query("departmentId") { description = "按部门 ID 过滤，例如 90=Executive" }
            query("jobId") { description = "按岗位编码精确过滤，例如 IT_PROG" }
            query("q") { description = "姓名或邮箱模糊搜索，例如 King" }
        }
        responseExamples(ApiList(total = 107, items = listOf(sampleEmployee)))
    }

    /** 新增员工并刷新缓存。employeeId 由调用方提供。 */
    post("/employees") {
        val body = call.receive<EmployeeCreateRequest>()
        if (body.lastName.isBlank() || body.email.isBlank() || body.jobId.isBlank() || body.hireDate.isBlank()) {
            return@post call.respondFail(HttpStatusCode.BadRequest, "lastName, email, hireDate, jobId are required")
        }
        if (runCatching { LocalDate.parse(body.hireDate) }.isFailure) {
            return@post call.respondFail(HttpStatusCode.BadRequest, "invalid hireDate, expected yyyy-MM-dd")
        }
        val created = EmployeeService.createEmployee(body)
        call.respondOk(created, message = "created")
    }.describe {
        summary = "新增员工并刷新缓存"
        tag("employees")
        requestExample(sampleEmployeeCreate, description = "employeeId、lastName、email、hireDate、jobId 必填")
        responseExamples(
            sampleEmployee.copy(
                employeeId = 999,
                firstName = "New",
                lastName = "Hire",
                email = "NHIRE999",
                phoneNumber = "515.000.0000",
                hireDate = "2026-09-18",
                jobId = "IT_PROG",
                salary = 5000.0,
                departmentId = 60,
            ),
            message = "created",
            okDescription = "创建成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "lastName, email, hireDate, jobId are required",
                HttpStatusCode.Conflict to "create failed",
            ),
        )
    }

    /** 按主键查询员工。 */
    get("/employees/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respondFail(HttpStatusCode.BadRequest, "invalid employee id")
        val cached = EmployeeService.findEmployee(id)
        call.cacheHeader(cached)
        val employee = cached.value
            ?: return@get call.respondFail(HttpStatusCode.NotFound, "employee not found")
        call.respondOk(employee)
    }.describe {
        summary = "按主键查询员工"
        tag("employees")
        parameters {
            path("id") { description = "员工主键 employee_id，例如 100" }
        }
        responseExamples(
            sampleEmployee,
            fails = arrayOf(
                HttpStatusCode.BadRequest to "invalid employee id",
                HttpStatusCode.NotFound to "employee not found",
            ),
        )
    }

    /** 部分更新员工并刷新 Redis 实体缓存、清除列表缓存。 */
    put("/employees/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid employee id")
        val patch = call.receive<EmployeeUpdateRequest>()
        if (!patch.hasUpdates()) {
            return@put call.respondFail(HttpStatusCode.BadRequest, "no fields to update")
        }
        patch.contentError()?.let { return@put call.respondFail(HttpStatusCode.BadRequest, it) }
        val updated = EmployeeService.updateEmployee(id, patch)
            ?: return@put call.respondFail(HttpStatusCode.NotFound, "employee not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "部分更新员工并刷新缓存"
        tag("employees")
        parameters {
            path("id") { description = "员工主键" }
        }
        requestExample(sampleEmployeePatch)
        responseExamples(
            sampleEmployee,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "no fields to update",
                HttpStatusCode.NotFound to "employee not found",
                HttpStatusCode.Conflict to "update failed",
            ),
        )
    }

    /** 精确的部分更新：只有请求体里出现的字段才会修改，显式 null 表示清空可空列。 */
    patch("/employees/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@patch call.respondFail(HttpStatusCode.BadRequest, "invalid employee id")
        val body = call.receive<JsonObject>()
        val patch = try {
            EmployeePatch.parse(body)
        } catch (cause: IllegalArgumentException) {
            return@patch call.respondFail(HttpStatusCode.BadRequest, cause.message ?: "invalid patch")
        }
        val updated = EmployeeService.patchEmployee(id, patch)
            ?: return@patch call.respondFail(HttpStatusCode.NotFound, "employee not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "精确部分更新员工（可清空可空字段）"
        tag("employees")
        parameters {
            path("id") { description = "员工主键" }
        }
        requestExample(sampleEmployeePatch, "只提交要改的字段；\"departmentId\": null 表示清空部门")
        responseExamples(
            sampleEmployee,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "unknown fields: nickname",
                HttpStatusCode.NotFound to "employee not found",
                HttpStatusCode.Conflict to "resource conflict",
            ),
        )
    }

    /** 删除员工并清除相关缓存。若被任职历史等外键引用会失败。 */
    delete("/employees/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@delete call.respondFail(HttpStatusCode.BadRequest, "invalid employee id")
        val deleted = EmployeeService.deleteEmployee(id)
        if (!deleted) {
            return@delete call.respondFail(HttpStatusCode.NotFound, "employee not found")
        }
        call.respondOk("deleted")
    }.describe {
        summary = "删除员工并清除相关缓存"
        tag("employees")
        parameters {
            path("id") { description = "员工主键" }
        }
        responseExamples(
            "deleted",
            fails = arrayOf(
                HttpStatusCode.NotFound to "employee not found",
                HttpStatusCode.Conflict to "delete failed",
            ),
        )
    }

    /** 员工详情，JOIN 岗位、部门、地点、国家、地区。 */
    get("/employees/{id}/details") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respondFail(HttpStatusCode.BadRequest, "invalid employee id")
        val cached = EmployeeService.findEmployeeDetail(id)
        call.cacheHeader(cached)
        val detail = cached.value
            ?: return@get call.respondFail(HttpStatusCode.NotFound, "employee not found")
        call.respondOk(detail)
    }.describe {
        summary = "员工详情（多表 JOIN）"
        tag("employees")
        parameters {
            path("id") { description = "员工主键" }
        }
        responseExamples(
            sampleEmployeeDetail,
            fails = arrayOf(HttpStatusCode.NotFound to "employee not found"),
        )
    }

    /** 员工详情视图 emp_details_view 分页。 */
    get("/emp-details") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
        val cached = EmployeeService.listEmployeeDetails(limit, offset)
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "员工详情视图分页"
        tag("employees")
        parameters {
            query("limit") { description = "每页条数，默认 50，最大 200" }
            query("offset") { description = "跳过条数，默认 0" }
        }
        responseExamples(ApiList(total = 106, items = listOf(sampleEmployeeDetail)))
    }
}
