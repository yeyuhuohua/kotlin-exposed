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
    /** 查询部门列表。 */
    suspend fun listDepartments(): List<DepartmentDto> = dbQuery {
        Departments.selectAll()
            .orderBy(Departments.departmentId to SortOrder.ASC)
            .map { it.toDepartment() }
            .toList()
    }

    /** 查询部门。 */
    suspend fun findDepartment(id: Int): DepartmentDto? = dbQuery {
        Departments.selectAll()
            .where { Departments.departmentId eq id }
            .map { it.toDepartment() }
            .firstOrNull()
    }

    /** 更新部门。 */
    suspend fun updateDepartment(id: Int, patch: DepartmentUpdateRequest): DepartmentDto? = dbUpdate {
        val rows = Departments.update({ Departments.departmentId eq id }) {
            patch.departmentName?.let { value -> it[departmentName] = value }
            patch.managerId?.let { value -> it[managerId] = value }
            patch.locationId?.let { value -> it[locationId] = value }
        }
        if (rows == 0) null else loadDepartment(id)
    }

    /** 新增部门。 */
    suspend fun createDepartment(body: DepartmentCreateRequest): DepartmentDto = dbUpdate {
        Departments.insert {
            it[departmentId] = body.departmentId
            it[departmentName] = body.departmentName
            it[managerId] = body.managerId
            it[locationId] = body.locationId
        }
        loadDepartment(body.departmentId) ?: error("department insert missing row")
    }

    /** 加载部门。 */
    private suspend fun loadDepartment(id: Int): DepartmentDto? =
        Departments.selectAll()
            .where { Departments.departmentId eq id }
            .map { it.toDepartment() }
            .firstOrNull()
}

/** 将持久层数据转换为部门传输对象。 */
private fun ResultRow.toDepartment() = DepartmentDto(
    departmentId = this[Departments.departmentId],
    departmentName = this[Departments.departmentName],
    managerId = this[Departments.managerId],
    locationId = this[Departments.locationId],
)
