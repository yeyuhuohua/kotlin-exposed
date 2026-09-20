package com.atguigu.hr.jobhistory

import com.atguigu.hr.config.DatabaseFactory.dbQuery
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.r2dbc.selectAll

/** 任职历史持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object JobHistoryRepository {
    suspend fun listJobHistory(): List<JobHistoryDto> = dbQuery {
        JobHistory.selectAll()
            .orderBy(JobHistory.employeeId to SortOrder.ASC)
            .map { it.toJobHistory() }
            .toList()
    }
}

private fun ResultRow.toJobHistory() = JobHistoryDto(
    employeeId = this[JobHistory.employeeId],
    startDate = this[JobHistory.startDate].toString(),
    endDate = this[JobHistory.endDate].toString(),
    jobId = this[JobHistory.jobId],
    departmentId = this[JobHistory.departmentId],
)
