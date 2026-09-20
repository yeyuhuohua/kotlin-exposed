package com.atguigu.hr.department

import com.atguigu.hr.common.api.cacheHeader
import com.atguigu.hr.common.api.respondFail
import com.atguigu.hr.common.api.respondOk
import com.atguigu.hr.docs.requestExample
import com.atguigu.hr.docs.responseExamples
import com.atguigu.hr.employee.sampleEmployee
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Route.departmentRoutes() {
    /**
     * 全部部门。
     *
     * Tag: departments
     * Response: 200 application/json [DepartmentDto] 部门列表
     */
    get("/departments") {
        val cached = DepartmentService.listDepartments()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "全部部门"
        tag("departments")
        responseExamples(listOf(sampleDepartment))
    }

    /**
     * 新增部门并刷新缓存。departmentId 由调用方提供。
     *
     * Tag: departments
     * Body: application/json [DepartmentCreateRequest] departmentId、departmentName 必填
     * Response: 200 application/json [DepartmentDto] 新建部门
     * Response: 400 名称为空
     * Response: 409 主键冲突或外键失败
     */
    post("/departments") {
        val body = call.receive<DepartmentCreateRequest>()
        if (body.departmentName.isBlank()) {
            return@post call.respondFail(HttpStatusCode.BadRequest, "departmentName is required")
        }
        val created = DepartmentService.createDepartment(body)
        call.respondOk(created, message = "created")
    }.describe {
        summary = "新增部门并刷新缓存"
        tag("departments")
        requestExample(sampleDepartmentCreate, description = "departmentId、departmentName 必填")
        responseExamples(
            sampleDepartment.copy(departmentId = 280, departmentName = "Kotlin Lab"),
            message = "created",
            okDescription = "创建成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "departmentName is required",
                HttpStatusCode.Conflict to "create failed",
            ),
        )
    }

    /**
     * 按主键查询部门。
     *
     * Tag: departments
     * Path: [Int] id 部门主键 department_id，例如 90
     * Response: 200 application/json [DepartmentDto] 部门信息
     * Response: 400 无效 ID
     * Response: 404 部门不存在
     */
    get("/departments/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respondFail(HttpStatusCode.BadRequest, "invalid department id")
        val cached = DepartmentService.findDepartment(id)
        call.cacheHeader(cached)
        val department = cached.value
            ?: return@get call.respondFail(HttpStatusCode.NotFound, "department not found")
        call.respondOk(department)
    }.describe {
        summary = "按主键查询部门"
        tag("departments")
        parameters {
            path("id") { description = "部门主键 department_id，例如 90" }
        }
        responseExamples(
            sampleDepartment,
            fails = arrayOf(HttpStatusCode.NotFound to "department not found"),
        )
    }

    /**
     * 部分更新部门并刷新缓存。
     *
     * Tag: departments
     * Path: [Int] id 部门主键
     * Body: application/json [DepartmentUpdateRequest] 只提交要改的字段
     * Response: 200 application/json [DepartmentDto] 更新后的部门
     * Response: 400 无更新字段或 ID 无效
     * Response: 404 部门不存在
     * Response: 409 外键等约束冲突
     */
    put("/departments/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid department id")
        val patch = call.receive<DepartmentUpdateRequest>()
        if (!patch.hasUpdates()) {
            return@put call.respondFail(HttpStatusCode.BadRequest, "no fields to update")
        }
        patch.contentError()?.let { return@put call.respondFail(HttpStatusCode.BadRequest, it) }
        val updated = DepartmentService.updateDepartment(id, patch)
            ?: return@put call.respondFail(HttpStatusCode.NotFound, "department not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "部分更新部门并刷新缓存"
        tag("departments")
        parameters {
            path("id") { description = "部门主键 department_id，例如 90" }
        }
        requestExample(sampleDepartmentPatch)
        responseExamples(
            sampleDepartment,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "no fields to update",
                HttpStatusCode.NotFound to "department not found",
                HttpStatusCode.Conflict to "update failed",
            ),
        )
    }

    /**
     * 某部门下的员工。
     *
     * Tag: departments
     * Path: [Int] id 部门主键
     * Response: 200 application/json [EmployeeDto] 该部门员工
     */
    get("/departments/{id}/employees") {
        val id = call.parameters["id"]?.toIntOrNull()
            ?: return@get call.respondFail(HttpStatusCode.BadRequest, "invalid department id")
        val cached = DepartmentService.listEmployeesByDepartment(id)
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "某部门下的员工"
        tag("departments")
        parameters {
            path("id") { description = "部门主键" }
        }
        responseExamples(listOf(sampleEmployee))
    }
}
