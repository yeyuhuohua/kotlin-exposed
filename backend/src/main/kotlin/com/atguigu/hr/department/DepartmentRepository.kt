package com.atguigu.hr.department

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

/** 部门持久层：使用 R2DBC 挂起事务，并在事务结束前收集查询结果。 */

object DepartmentRepository {
    suspend fun listDepartments(): List<DepartmentDto> = dbQuery {
        Departments.selectAll()
            .orderBy(Departments.departmentId to SortOrder.ASC)
            .map { it.toDepartment() }
            .toList()
    }

    suspend fun findDepartment(id: Int): DepartmentDto? = dbQuery {
        Departments.selectAll()
            .where { Departments.departmentId eq id }
            .map { it.toDepartment() }
            .firstOrNull()
    }

    suspend fun updateDepartment(id: Int, patch: DepartmentUpdateRequest): DepartmentDto? = dbUpdate {
        val rows = Departments.update({ Departments.departmentId eq id }) {
            patch.departmentName?.let { value -> it[departmentName] = value }
            patch.managerId?.let { value -> it[managerId] = value }
            patch.locationId?.let { value -> it[locationId] = value }
        }
        if (rows == 0) null else loadDepartment(id)
    }

    suspend fun createDepartment(body: DepartmentCreateRequest): DepartmentDto = dbUpdate {
        Departments.insert {
            it[departmentId] = body.departmentId
            it[departmentName] = body.departmentName
            it[managerId] = body.managerId
            it[locationId] = body.locationId
        }
        loadDepartment(body.departmentId) ?: error("department insert missing row")
    }

    private suspend fun loadDepartment(id: Int): DepartmentDto? =
        Departments.selectAll()
            .where { Departments.departmentId eq id }
            .map { it.toDepartment() }
            .firstOrNull()
}

private fun ResultRow.toDepartment() = DepartmentDto(
    departmentId = this[Departments.departmentId],
    departmentName = this[Departments.departmentName],
    managerId = this[Departments.managerId],
    locationId = this[Departments.locationId],
)
