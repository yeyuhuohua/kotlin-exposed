package com.atguigu.hr.job

import com.atguigu.hr.config.DatabaseFactory.dbQuery
import com.atguigu.hr.config.DatabaseFactory.dbUpdate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

/** 岗位持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object JobRepository {
    suspend fun listJobs(): List<JobDto> = dbQuery {
        Jobs.selectAll()
            .orderBy(Jobs.jobId to SortOrder.ASC)
            .map { it.toJob() }
            .toList()
    }

    suspend fun findJob(jobId: String): JobDto? = dbQuery {
        Jobs.selectAll()
            .where { Jobs.jobId eq jobId }
            .map { it.toJob() }
            .firstOrNull()
    }

    suspend fun updateJob(jobId: String, patch: JobUpdateRequest): JobDto? = dbUpdate {
        val rows = Jobs.update({ Jobs.jobId eq jobId }) {
            patch.jobTitle?.let { value -> it[jobTitle] = value }
            patch.minSalary?.let { value -> it[minSalary] = value }
            patch.maxSalary?.let { value -> it[maxSalary] = value }
        }
        if (rows == 0) null else loadJob(jobId)
    }

    suspend fun createJob(body: JobCreateRequest): JobDto = dbUpdate {
        Jobs.insert {
            it[jobId] = body.jobId
            it[jobTitle] = body.jobTitle
            it[minSalary] = body.minSalary
            it[maxSalary] = body.maxSalary
        }
        loadJob(body.jobId) ?: error("job insert missing row")
    }

    private suspend fun loadJob(jobId: String): JobDto? =
        Jobs.selectAll()
            .where { Jobs.jobId eq jobId }
            .map { it.toJob() }
            .firstOrNull()
}

private fun ResultRow.toJob() = JobDto(
    jobId = this[Jobs.jobId],
    jobTitle = this[Jobs.jobTitle],
    minSalary = this[Jobs.minSalary],
    maxSalary = this[Jobs.maxSalary],
)
