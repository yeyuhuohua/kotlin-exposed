package com.atguigu.hr.demo.temp

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

/** 示例人员持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object TEmpRepository {
    suspend fun listTEmp(): List<TEmpDto> = dbQuery {
        TEmp.selectAll()
            .orderBy(TEmp.id to SortOrder.ASC)
            .map { it.toTEmp() }
            .toList()
    }

    suspend fun findTEmp(id: Int): TEmpDto? = dbQuery {
        TEmp.selectAll()
            .where { TEmp.id eq id }
            .map { it.toTEmp() }
            .firstOrNull()
    }

    suspend fun updateTEmp(id: Int, patch: TEmpUpdateRequest): TEmpDto? = dbUpdate {
        val rows = TEmp.update({ TEmp.id eq id }) {
            patch.name?.let { value -> it[name] = value }
            patch.age?.let { value -> it[age] = value }
            patch.deptId?.let { value -> it[deptId] = value }
            patch.empno?.let { value -> it[empno] = value }
        }
        if (rows == 0) null else loadTEmp(id)
    }

    suspend fun createTEmp(body: TEmpCreateRequest): TEmpDto = dbUpdate {
        val stmt = TEmp.insert {
            it[name] = body.name
            it[age] = body.age
            it[deptId] = body.deptId
            it[empno] = body.empno
        }
        loadTEmp(stmt[TEmp.id]) ?: error("t_emp insert missing row")
    }

    private suspend fun loadTEmp(id: Int): TEmpDto? =
        TEmp.selectAll()
            .where { TEmp.id eq id }
            .map { it.toTEmp() }
            .firstOrNull()
}

private fun ResultRow.toTEmp() = TEmpDto(
    id = this[TEmp.id],
    name = this[TEmp.name],
    age = this[TEmp.age],
    deptId = this[TEmp.deptId],
    empno = this[TEmp.empno],
)
