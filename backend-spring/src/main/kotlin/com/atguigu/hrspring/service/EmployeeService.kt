package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.LikeEscape
import com.atguigu.hrspring.common.api.PageParams
import com.atguigu.hrspring.common.api.selectPage
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.DepartmentHeadcountDto
import com.atguigu.hrspring.dto.EmployeeCreateRequest
import com.atguigu.hrspring.dto.EmployeeDetailDto
import com.atguigu.hrspring.dto.EmployeeDto
import com.atguigu.hrspring.dto.EmployeePatch
import com.atguigu.hrspring.dto.EmployeeUpdateRequest
import com.atguigu.hrspring.dto.SalarySummaryDto
import com.atguigu.hrspring.dto.UNASSIGNED_DEPARTMENT
import com.atguigu.hrspring.entity.EmpDetailsViewEntity
import com.atguigu.hrspring.entity.EmployeeEntity
import com.atguigu.hrspring.mapper.EmpDetailsViewMapper
import com.atguigu.hrspring.mapper.EmployeeMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

/** 员工查询与写入。写操作失效员工缓存和概览缓存。 */
@Validated
@Service
class EmployeeService(
    private val employeeMapper: EmployeeMapper,
    private val empDetailsViewMapper: EmpDetailsViewMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(
        cacheNames = [CacheNames.EMPLOYEES],
        key = "T(com.atguigu.hrspring.common.cache.CacheKeys).employees(#page, #departmentId, #jobId, #q, #unassignedOnly)",
    )
    fun list(
        page: PageParams,
        departmentId: Int?,
        jobId: String?,
        q: String?,
        unassignedOnly: Boolean,
    ): ApiList<EmployeeDto> {
        val rows = employeeMapper.selectPage(page) {
            applyEmployeeFilters(departmentId, jobId, q, unassignedOnly)
            orderByAsc("employee_id")
        }
        return ApiList(rows.total, rows.items.map { it.toDto() })
    }

    @Cacheable(cacheNames = [CacheNames.EMPLOYEES_BY_DEPT], key = "#departmentId")
    fun listByDepartment(departmentId: Int): List<EmployeeDto> =
        employeeMapper.selectList(
            QueryWrapper<EmployeeEntity>()
                .eq("department_id", departmentId)
                .orderByAsc("employee_id"),
        ).map { it.toDto() }

    /** 概览会缓存聚合结果，这里不单独进 Redis。 */
    fun countByDepartment(): List<DepartmentHeadcountDto> =
        employeeMapper.countByDepartment().map { row ->
            DepartmentHeadcountDto(
                departmentId = row.getAny("departmentId").asInt(),
                departmentName = row.getAny("departmentName").asText() ?: UNASSIGNED_DEPARTMENT,
                count = row.getAny("headcount").asLong(),
            )
        }

    /** 概览会缓存聚合结果，这里不单独进 Redis。 */
    fun salarySummary(): SalarySummaryDto {
        val row = employeeMapper.selectMaps(
            QueryWrapper<EmployeeEntity>().select(
                "COUNT(salary) AS employeesWithSalary",
                "SUM(salary) AS totalSalary",
                "AVG(salary) AS averageSalary",
                "MIN(salary) AS minSalary",
                "MAX(salary) AS maxSalary",
            ),
        ).firstOrNull().orEmpty()
        return SalarySummaryDto(
            employeesWithSalary = row.getAny("employeesWithSalary").asLong(),
            totalSalary = row.getAny("totalSalary").asDouble() ?: 0.0,
            averageSalary = row.getAny("averageSalary").asDouble(),
            minSalary = row.getAny("minSalary").asDouble(),
            maxSalary = row.getAny("maxSalary").asDouble(),
        )
    }

    @Cacheable(cacheNames = [CacheNames.EMPLOYEE], key = "#id")
    fun get(id: Int): EmployeeDto =
        employeeMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("employee not found")

    @Cacheable(cacheNames = [CacheNames.EMPLOYEE_DETAILS], key = "#id")
    fun getDetail(id: Int): EmployeeDetailDto =
        employeeMapper.findDetail(id)?.toDetail()
            ?: throw ApiException.notFound("employee not found")

    @Cacheable(cacheNames = [CacheNames.EMP_DETAILS], key = "#page.limit + ':' + #page.offset")
    fun listDetails(page: PageParams): ApiList<EmployeeDetailDto> {
        val rows = empDetailsViewMapper.selectPage(page) {
            orderByAsc("employee_id")
        }
        return ApiList(rows.total, rows.items.map { it.toDto() })
    }

    @EvictEmployeeGraphs
    @Transactional
    fun create(@Valid request: EmployeeCreateRequest): EmployeeDto {
        return write("create failed") {
            val entity = EmployeeEntity().apply {
                employeeId = request.employeeId!!
                firstName = request.firstName
                lastName = request.lastName!!
                email = request.email!!
                phoneNumber = request.phoneNumber
                hireDate = LocalDate.parse(request.hireDate!!)
                jobId = request.jobId!!
                salary = request.salary
                commissionPct = request.commissionPct
                managerId = request.managerId
                departmentId = request.departmentId
            }
            employeeMapper.insert(entity)
            employeeMapper.selectById(request.employeeId!!)?.toDto()
                ?: error("employee insert missing row")
        }
    }

    @EvictEmployeeGraphs
    @Transactional
    fun update(id: Int, @Valid request: EmployeeUpdateRequest): EmployeeDto {
        return write("update failed") {
            lock(id) ?: throw ApiException.notFound("employee not found")
            val wrapper = UpdateWrapper<EmployeeEntity>().eq("employee_id", id)
            request.salary?.let { wrapper.set("salary", it) }
            request.departmentId?.let { wrapper.set("department_id", it) }
            request.jobId?.let { wrapper.set("job_id", it) }
            request.phoneNumber?.let { wrapper.set("phone_number", it) }
            employeeMapper.update(wrapper)
            load(id)
        }
    }

    @EvictEmployeeGraphs
    @Transactional
    fun patch(id: Int, @Valid patch: EmployeePatch): EmployeeDto {
        return write("resource conflict") {
            lock(id) ?: throw ApiException.notFound("employee not found")
            val wrapper = UpdateWrapper<EmployeeEntity>().eq("employee_id", id)
            if ("firstName" in patch.present) wrapper.assign("first_name", patch.firstName)
            if ("salary" in patch.present) wrapper.assign("salary", patch.salary)
            if ("commissionPct" in patch.present) wrapper.assign("commission_pct", patch.commissionPct)
            if ("departmentId" in patch.present) wrapper.assign("department_id", patch.departmentId)
            if ("managerId" in patch.present) wrapper.assign("manager_id", patch.managerId)
            if ("phoneNumber" in patch.present) wrapper.assign("phone_number", patch.phoneNumber)
            if ("jobId" in patch.present) wrapper.set("job_id", patch.jobId)
            employeeMapper.update(wrapper)
            load(id)
        }
    }

    @EvictEmployeeGraphs
    @Transactional
    fun delete(id: Int) {
        write("delete failed") {
            val rows = employeeMapper.deleteById(id)
            if (rows == 0) throw ApiException.notFound("employee not found")
        }
    }

    /** selectList 走当前事务。selectOne 会另开 SqlSession，FOR UPDATE 锁不住后面的更新。 */
    private fun lock(id: Int): EmployeeEntity? =
        employeeMapper.selectList(
            QueryWrapper<EmployeeEntity>()
                .eq("employee_id", id)
                .last("FOR UPDATE"),
        ).firstOrNull()

    private fun load(id: Int): EmployeeDto =
        employeeMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("employee not found")

    private fun <T> write(conflictMessage: String, block: () -> T): T {
        try {
            return block()
        } catch (e: DataIntegrityViolationException) {
            log.warn("constraint conflict: {}", e.message)
            throw ApiException.conflict(conflictMessage)
        }
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@CacheEvict(
    cacheNames = [
        CacheNames.EMPLOYEES,
        CacheNames.EMPLOYEES_BY_DEPT,
        CacheNames.EMPLOYEE,
        CacheNames.EMPLOYEE_DETAILS,
        CacheNames.EMP_DETAILS,
        CacheNames.OVERVIEW,
    ],
    allEntries = true,
)
internal annotation class EvictEmployeeGraphs

private fun QueryWrapper<EmployeeEntity>.applyEmployeeFilters(
    departmentId: Int?,
    jobId: String?,
    q: String?,
    unassignedOnly: Boolean,
) {
    if (unassignedOnly) {
        isNull("department_id")
    } else if (departmentId != null) {
        eq("department_id", departmentId)
    }
    if (jobId != null) {
        eq("job_id", jobId)
    }
    val keyword = q?.takeIf { it.isNotBlank() }?.trim()
    if (keyword != null) {
        val pattern = LikeEscape.containsPattern(keyword)
        apply(
            "(first_name LIKE {0} ESCAPE '\\\\' OR last_name LIKE {1} ESCAPE '\\\\' OR email LIKE {2} ESCAPE '\\\\')",
            pattern,
            pattern,
            pattern,
        )
    }
}

/** 可空列写 null 时用 SQL NULL，避免 JDBC 把空参数当成 OTHER。 */
private fun UpdateWrapper<EmployeeEntity>.assign(column: String, value: Any?) {
    if (value == null) {
        setSql("$column = NULL")
    } else {
        set(column, value)
    }
}

private fun EmployeeEntity.toDto() = EmployeeDto(
    employeeId = checkNotNull(employeeId),
    firstName = firstName,
    lastName = lastName,
    email = email,
    phoneNumber = phoneNumber,
    hireDate = hireDate.toString(),
    jobId = jobId,
    salary = salary,
    commissionPct = commissionPct,
    managerId = managerId,
    departmentId = departmentId,
)

private fun EmpDetailsViewEntity.toDto() = EmployeeDetailDto(
    employeeId = employeeId,
    firstName = firstName,
    lastName = lastName,
    jobId = jobId,
    jobTitle = jobTitle,
    salary = salary,
    commissionPct = commissionPct,
    managerId = managerId,
    departmentId = departmentId,
    departmentName = departmentName,
    locationId = locationId,
    city = city,
    stateProvince = stateProvince,
    countryId = countryId,
    countryName = countryName,
    regionName = regionName,
)

private fun Map<*, *>.toDetail(): EmployeeDetailDto {
    val joinedDepartmentId = getAny("departmentRowId").asInt()
    return EmployeeDetailDto(
        employeeId = getAny("employeeId").asInt() ?: 0,
        firstName = getAny("firstName").asText(),
        lastName = getAny("lastName").asText().orEmpty(),
        email = getAny("email").asText(),
        phoneNumber = getAny("phoneNumber").asText(),
        hireDate = getAny("hireDate").asIsoDate(),
        jobId = getAny("jobId").asText().orEmpty(),
        jobTitle = getAny("jobTitle").asText().orEmpty(),
        salary = getAny("salary").asDouble(),
        commissionPct = getAny("commissionPct").asDouble(),
        managerId = getAny("managerId").asInt(),
        departmentId = joinedDepartmentId ?: getAny("departmentId").asInt(),
        departmentName = getAny("departmentName").asText().orEmpty(),
        locationId = getAny("locationId").asInt(),
        city = getAny("city").asText().orEmpty(),
        stateProvince = getAny("stateProvince").asText(),
        countryId = getAny("countryId").asText(),
        countryName = getAny("countryName").asText(),
        regionName = getAny("regionName").asText(),
    )
}

private fun Map<*, *>.getAny(name: String): Any? {
    if (containsKey(name)) return this[name]
    val found = keys.firstOrNull { it is String && it.equals(name, ignoreCase = true) } ?: return null
    return this[found]
}

private fun Any?.asLong(): Long = when (this) {
    null -> 0L
    is BigDecimal -> toLong()
    is BigInteger -> toLong()
    is Number -> toLong()
    else -> toString().toLong()
}

private fun Any?.asDouble(): Double? = when (this) {
    null -> null
    is BigDecimal -> toDouble()
    is Number -> toDouble()
    else -> toString().toDoubleOrNull()
}

private fun Any?.asInt(): Int? = when (this) {
    null -> null
    is BigDecimal -> toInt()
    is BigInteger -> toInt()
    is Number -> toInt()
    else -> toString().toIntOrNull()
}

private fun Any?.asText(): String? = when (this) {
    null -> null
    is String -> this
    else -> toString()
}

private fun Any?.asIsoDate(): String? = when (this) {
    null -> null
    is String -> this
    is LocalDate -> toString()
    is java.sql.Date -> toLocalDate().toString()
    else -> toString()
}
