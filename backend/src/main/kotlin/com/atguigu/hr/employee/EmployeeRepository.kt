package com.atguigu.hr.employee

import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.config.DatabaseFactory.dbQuery
import com.atguigu.hr.config.DatabaseFactory.dbUpdate
import com.atguigu.hr.department.Departments
import com.atguigu.hr.geography.Countries
import com.atguigu.hr.geography.Regions
import com.atguigu.hr.job.Jobs
import com.atguigu.hr.location.Locations
import java.time.LocalDate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.avg
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.r2dbc.andWhere
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update

object EmployeeRepository {
    /** 支持 departmentId、jobId、姓名/邮箱模糊搜索。 */
    suspend fun listEmployees(
        limit: Int,
        offset: Long,
        departmentId: Int?,
        jobId: String?,
        q: String?,
    ): ApiList<EmployeeDto> = dbQuery {
        val query = Employees.selectAll()
        departmentId?.let { query.andWhere { Employees.departmentId eq it } }
        jobId?.let { query.andWhere { Employees.jobId eq it } }
        q?.takeIf { it.isNotBlank() }?.let { keyword ->
            // 依赖列的 *_ci 排序规则做大小写不敏感匹配，不再对列套 LOWER()：
            // 函数包裹会让每行都要重新计算，且彻底断掉用索引的可能。
            // 注意首尾都有 % 的包含匹配本身仍然无法走 B-tree 索引，数据量大时
            // 需要改成前缀匹配或引入全文检索（见 README 的"已知限制"）。
            val pattern = "%${keyword.trim()}%"
            query.andWhere {
                (Employees.firstName like pattern) or
                    (Employees.lastName like pattern) or
                    (Employees.email like pattern)
            }
        }
        val total = query.count()
        val items = query
            .orderBy(Employees.employeeId to SortOrder.ASC)
            .limit(limit)
            .offset(offset)
            .map { it.toEmployee() }
            .toList()
        ApiList(total = total, items = items)
    }

    suspend fun findEmployee(id: Int): EmployeeDto? = dbQuery {
        Employees.selectAll()
            .where { Employees.employeeId eq id }
            .map { it.toEmployee() }
            .firstOrNull()
    }

    /** 员工 INNER JOIN 岗位，其余 LEFT JOIN（部门可为空）。 */
    suspend fun findEmployeeDetail(id: Int): EmployeeDetailDto? = dbQuery {
        Employees
            .join(Jobs, JoinType.INNER, Employees.jobId, Jobs.jobId)
            .join(Departments, JoinType.LEFT, Employees.departmentId, Departments.departmentId)
            .join(Locations, JoinType.LEFT, Departments.locationId, Locations.locationId)
            .join(Countries, JoinType.LEFT, Locations.countryId, Countries.countryId)
            .join(Regions, JoinType.LEFT, Countries.regionId, Regions.regionId)
            .selectAll()
            .where { Employees.employeeId eq id }
            .map { it.toEmployeeDetail() }
            .firstOrNull()
    }

    /** 查询 emp_details_view。 */
    suspend fun listEmployeeDetails(limit: Int, offset: Long): ApiList<EmployeeDetailDto> = dbQuery {
        val query = EmpDetailsView.selectAll()
        val total = query.count()
        val items = query
            .orderBy(EmpDetailsView.employeeId to SortOrder.ASC)
            .limit(limit)
            .offset(offset)
            .map { it.toEmployeeDetailFromView() }
            .toList()
        ApiList(total = total, items = items)
    }

    suspend fun listEmployeesByDepartment(departmentId: Int): List<EmployeeDto> = dbQuery {
        Employees.selectAll()
            .where { Employees.departmentId eq departmentId }
            .orderBy(Employees.employeeId to SortOrder.ASC)
            .map { it.toEmployee() }
            .toList()
    }

    /** 按部门统计人数（含未分配部门的员工），供概览聚合使用，避免前端拉全量员工。 */
    suspend fun countEmployeesByDepartment(): List<DepartmentHeadcountDto> = dbQuery {
        val headcount = Employees.employeeId.count()
        Employees
            .join(Departments, JoinType.LEFT, Employees.departmentId, Departments.departmentId)
            .select(Employees.departmentId, Departments.departmentName, headcount)
            .groupBy(Employees.departmentId, Departments.departmentName)
            .orderBy(headcount to SortOrder.DESC)
            .map { row ->
                DepartmentHeadcountDto(
                    departmentId = row[Employees.departmentId],
                    departmentName = row.getOrNull(Departments.departmentName) ?: UNASSIGNED_DEPARTMENT,
                    count = row[headcount],
                )
            }
            .toList()
    }

    /** 薪资汇总在数据库里算，避免把整张员工表读到应用侧。 */
    suspend fun salarySummary(): SalarySummaryDto = dbQuery {
        val salary = Employees.salary
        val paid = salary.count()
        val total = salary.sum()
        val average = salary.avg()
        val lowest = salary.min()
        val highest = salary.max()
        val row = Employees.select(paid, total, average, lowest, highest).firstOrNull()
        SalarySummaryDto(
            employeesWithSalary = row?.get(paid) ?: 0L,
            totalSalary = row?.get(total) ?: 0.0,
            // AVG 在 Exposed 里返回 BigDecimal，其余聚合直接是 Double
            averageSalary = row?.get(average)?.toDouble(),
            minSalary = row?.get(lowest),
            maxSalary = row?.get(highest),
        )
    }

    suspend fun updateEmployee(id: Int, patch: EmployeeUpdateRequest): EmployeeDto? = dbUpdate {
        val rows = Employees.update({ Employees.employeeId eq id }) {
            patch.salary?.let { value -> it[salary] = value }
            patch.departmentId?.let { value -> it[departmentId] = value }
            patch.jobId?.let { value -> it[jobId] = value }
            patch.phoneNumber?.let { value -> it[phoneNumber] = value }
        }
        if (rows == 0) null else loadEmployee(id)
    }

    /** 只有 present 里的列会被写；可空列允许写入 null（清空）。 */
    suspend fun patchEmployee(id: Int, patch: EmployeePatch): EmployeeDto? = dbUpdate {
        val rows = Employees.update({ Employees.employeeId eq id }) { statement ->
            if ("firstName" in patch.present) statement[firstName] = patch.firstName
            if ("salary" in patch.present) statement[salary] = patch.salary
            if ("commissionPct" in patch.present) statement[commissionPct] = patch.commissionPct
            if ("departmentId" in patch.present) statement[departmentId] = patch.departmentId
            if ("managerId" in patch.present) statement[managerId] = patch.managerId
            if ("phoneNumber" in patch.present) statement[phoneNumber] = patch.phoneNumber
            patch.jobId?.let { value -> statement[jobId] = value }
        }
        if (rows == 0) null else loadEmployee(id)
    }

    suspend fun createEmployee(body: EmployeeCreateRequest): EmployeeDto = dbUpdate {
        Employees.insert {
            it[employeeId] = body.employeeId
            it[firstName] = body.firstName
            it[lastName] = body.lastName
            it[email] = body.email
            it[phoneNumber] = body.phoneNumber
            it[hireDate] = LocalDate.parse(body.hireDate)
            it[jobId] = body.jobId
            it[salary] = body.salary
            it[commissionPct] = body.commissionPct
            it[managerId] = body.managerId
            it[departmentId] = body.departmentId
        }
        loadEmployee(body.employeeId) ?: error("employee insert missing row")
    }

    private suspend fun loadEmployee(id: Int): EmployeeDto? =
        Employees.selectAll()
            .where { Employees.employeeId eq id }
            .map { it.toEmployee() }
            .firstOrNull()

    /** 返回删除行数；被 job_history 等外键引用时会抛异常。 */
    suspend fun deleteEmployee(id: Int): Int = dbUpdate {
        Employees.deleteWhere { Employees.employeeId eq id }
    }
}

/** ResultRow → API DTO。日期列转 ISO 字符串。 */
private fun ResultRow.toEmployee() = EmployeeDto(
    employeeId = this[Employees.employeeId],
    firstName = this[Employees.firstName],
    lastName = this[Employees.lastName],
    email = this[Employees.email],
    phoneNumber = this[Employees.phoneNumber],
    hireDate = this[Employees.hireDate].toString(),
    jobId = this[Employees.jobId],
    salary = this[Employees.salary],
    commissionPct = this[Employees.commissionPct],
    managerId = this[Employees.managerId],
    departmentId = this[Employees.departmentId],
)

/** LEFT JOIN 列可能为 null，用 getOrNull。 */
private fun ResultRow.toEmployeeDetail() = EmployeeDetailDto(
    employeeId = this[Employees.employeeId],
    firstName = this[Employees.firstName],
    lastName = this[Employees.lastName],
    email = this[Employees.email],
    phoneNumber = this[Employees.phoneNumber],
    hireDate = this[Employees.hireDate].toString(),
    jobId = this[Employees.jobId],
    jobTitle = this[Jobs.jobTitle],
    salary = this[Employees.salary],
    commissionPct = this[Employees.commissionPct],
    managerId = this[Employees.managerId],
    departmentId = this.getOrNull(Departments.departmentId) ?: this[Employees.departmentId],
    departmentName = this.getOrNull(Departments.departmentName).orEmpty(),
    locationId = this.getOrNull(Locations.locationId),
    city = this.getOrNull(Locations.city).orEmpty(),
    stateProvince = this.getOrNull(Locations.stateProvince),
    countryId = this.getOrNull(Countries.countryId),
    countryName = this.getOrNull(Countries.countryName),
    regionName = this.getOrNull(Regions.regionName),
)

private fun ResultRow.toEmployeeDetailFromView() = EmployeeDetailDto(
    employeeId = this[EmpDetailsView.employeeId],
    firstName = this[EmpDetailsView.firstName],
    lastName = this[EmpDetailsView.lastName],
    jobId = this[EmpDetailsView.jobId],
    jobTitle = this[EmpDetailsView.jobTitle],
    salary = this[EmpDetailsView.salary],
    commissionPct = this[EmpDetailsView.commissionPct],
    managerId = this[EmpDetailsView.managerId],
    departmentId = this[EmpDetailsView.departmentId],
    departmentName = this[EmpDetailsView.departmentName],
    locationId = this[EmpDetailsView.locationId],
    city = this[EmpDetailsView.city],
    stateProvince = this[EmpDetailsView.stateProvince],
    countryId = this[EmpDetailsView.countryId],
    countryName = this[EmpDetailsView.countryName],
    regionName = this[EmpDetailsView.regionName],
)
