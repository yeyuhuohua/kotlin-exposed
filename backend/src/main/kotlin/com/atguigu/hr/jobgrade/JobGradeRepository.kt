package com.atguigu.hr.jobgrade

import com.atguigu.hr.config.DatabaseFactory.dbQuery
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.r2dbc.selectAll

/** 薪资等级持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object JobGradeRepository {
    /** 查询薪资等级列表。 */
    suspend fun listJobGrades(): List<JobGradeDto> = dbQuery {
        JobGrades.selectAll()
            .orderBy(JobGrades.gradeLevel to SortOrder.ASC)
            .map { it.toJobGrade() }
            .toList()
    }
}

/** 将持久层数据转换为薪资等级传输对象。 */
private fun ResultRow.toJobGrade() = JobGradeDto(
    gradeLevel = this[JobGrades.gradeLevel],
    lowestSal = this[JobGrades.lowestSal],
    highestSal = this[JobGrades.highestSal],
)
