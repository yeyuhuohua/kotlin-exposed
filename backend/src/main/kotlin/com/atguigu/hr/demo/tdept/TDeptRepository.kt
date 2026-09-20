package com.atguigu.hr.demo.tdept

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

/** 示例部门持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object TDeptRepository {
    suspend fun listTDept(): List<TDeptDto> = dbQuery {
        TDept.selectAll()
            .orderBy(TDept.id to SortOrder.ASC)
            .map { it.toTDept() }
            .toList()
    }

    suspend fun findTDept(id: Int): TDeptDto? = dbQuery {
        TDept.selectAll()
            .where { TDept.id eq id }
            .map { it.toTDept() }
            .firstOrNull()
    }

    suspend fun updateTDept(id: Int, patch: TDeptUpdateRequest): TDeptDto? = dbUpdate {
        val rows = TDept.update({ TDept.id eq id }) {
            patch.deptName?.let { value -> it[deptName] = value }
            patch.address?.let { value -> it[address] = value }
        }
        if (rows == 0) null else loadTDept(id)
    }

    suspend fun createTDept(body: TDeptCreateRequest): TDeptDto = dbUpdate {
        val stmt = TDept.insert {
            it[deptName] = body.deptName
            it[address] = body.address
        }
        loadTDept(stmt[TDept.id]) ?: error("t_dept insert missing row")
    }

    private suspend fun loadTDept(id: Int): TDeptDto? =
        TDept.selectAll()
            .where { TDept.id eq id }
            .map { it.toTDept() }
            .firstOrNull()
}

private fun ResultRow.toTDept() = TDeptDto(
    id = this[TDept.id],
    deptName = this[TDept.deptName],
    address = this[TDept.address],
)
