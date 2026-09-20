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
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import java.time.LocalDate

@OptIn(ExperimentalKtorApi::class)
fun Route.employeeRoutes() {
    /**
     * 分页查询员工，支持部门、岗位、关键字筛选。
     *
     * Tag: employees
     * Query: [Int] limit 每页条数，默认 50，最大 200
     * Query: [Long] offset 跳过条数，默认 0。limit=50&offset=50 为第二页
     * Query: [Int] departmentId 按部门 ID 过滤，例如 90=Executive
     * Query: [String] jobId 按岗位编码精确过滤，例如 IT_PROG
     * Query: [String] q 姓名或邮箱模糊搜索，例如 King
     * Response: 200 application/json [ApiList] 员工分页列表
     */
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

    /**
     * 新增员工并刷新缓存。employeeId 由调用方提供。
     *
     * Tag: employees
     * Body: application/json [EmployeeCreateRequest] employeeId、lastName、email、hireDate、jobId 必填
     * Response: 200 application/json [EmployeeDto] 新建员工
     * Response: 400 必填字段缺失或日期格式错误
     * Response: 409 主键/邮箱冲突或外键失败
     */
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

    /**
     * 按主键查询员工。
     *
     * Tag: employees
     * Path: [Int] id 员工主键 employee_id，例如 100
     * Response: 200 application/json [EmployeeDto] 员工信息
     * Response: 400 无效 ID
     * Response: 404 员工不存在
     */
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

    /**
     * 部分更新员工并刷新 Redis 实体缓存、清除列表缓存。
     *
     * Tag: employees
     * Path: [Int] id 员工主键
     * Body: application/json [EmployeeUpdateRequest] 只提交要改的字段
     * Response: 200 application/json [EmployeeDto] 更新后的员工
     * Response: 400 无更新字段或 ID 无效
     * Response: 404 员工不存在
     * Response: 409 外键等约束冲突
     */
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

    /**
     * 删除员工并清除相关缓存。若被任职历史等外键引用会失败。
     *
     * Tag: employees
     * Path: [Int] id 员工主键
     * Response: 200 已删除
     * Response: 404 员工不存在
     * Response: 409 外键约束冲突
     */
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

    /**
     * 员工详情，JOIN 岗位、部门、地点、国家、地区。
     *
     * Tag: employees
     * Path: [Int] id 员工主键
     * Response: 200 application/json [EmployeeDetailDto] 含岗位部门地点
     * Response: 400 无效 ID
     * Response: 404 员工不存在
     */
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

    /**
     * 员工详情视图 emp_details_view 分页。
     *
     * Tag: employees
     * Query: [Int] limit 每页条数，默认 50，最大 200
     * Query: [Long] offset 跳过条数，默认 0
     * Response: 200 application/json [ApiList] 详情分页列表
     */
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
