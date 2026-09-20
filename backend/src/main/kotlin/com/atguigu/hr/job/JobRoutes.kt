package com.atguigu.hr.job

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
fun Route.jobRoutes() {
    /** 全部岗位。 */
    get("/jobs") {
        val cached = JobService.listJobs()
        call.cacheHeader(cached)
        call.respondOk(cached.value)
    }.describe {
        summary = "全部岗位"
        tag("jobs")
        responseExamples(listOf(sampleJob))
    }

    /** 新增岗位并刷新缓存。 */
    post("/jobs") {
        val body = call.receive<JobCreateRequest>()
        if (body.jobId.isBlank() || body.jobTitle.isBlank()) {
            return@post call.respondFail(HttpStatusCode.BadRequest, "jobId and jobTitle are required")
        }
        val created = JobService.createJob(body)
        call.respondOk(created, message = "created")
    }.describe {
        summary = "新增岗位并刷新缓存"
        tag("jobs")
        requestExample(sampleJobCreate, description = "jobId、jobTitle 必填")
        responseExamples(
            sampleJob.copy(jobId = "KT_DEV", jobTitle = "Kotlin Developer", minSalary = 4000, maxSalary = 9000),
            message = "created",
            okDescription = "创建成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "jobId and jobTitle are required",
                HttpStatusCode.Conflict to "create failed",
            ),
        )
    }

    /** 部分更新岗位并刷新缓存。 */
    put("/jobs/{id}") {
        val id = call.parameters["id"]?.takeIf { it.isNotBlank() }
            ?: return@put call.respondFail(HttpStatusCode.BadRequest, "invalid job id")
        val patch = call.receive<JobUpdateRequest>()
        if (!patch.hasUpdates()) {
            return@put call.respondFail(HttpStatusCode.BadRequest, "no fields to update")
        }
        patch.contentError()?.let { return@put call.respondFail(HttpStatusCode.BadRequest, it) }
        val updated = JobService.updateJob(id, patch)
            ?: return@put call.respondFail(HttpStatusCode.NotFound, "job not found")
        call.respondOk(updated, message = "updated")
    }.describe {
        summary = "部分更新岗位并刷新缓存"
        tag("jobs")
        parameters {
            path("id") { description = "岗位编码 job_id，例如 AD_PRES" }
        }
        requestExample(sampleJobPatch)
        responseExamples(
            sampleJob,
            message = "updated",
            okDescription = "更新成功",
            fails = arrayOf(
                HttpStatusCode.BadRequest to "no fields to update",
                HttpStatusCode.NotFound to "job not found",
                HttpStatusCode.Conflict to "update failed",
            ),
        )
    }
}
