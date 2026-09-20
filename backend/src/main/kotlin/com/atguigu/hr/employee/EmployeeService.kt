package com.atguigu.hr.employee

import com.atguigu.hr.common.api.ApiList
import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.overview.OverviewCache

/** 协调员工查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object EmployeeService {
    suspend fun listEmployees(
        limit: Int,
        offset: Long,
        departmentId: Int?,
        jobId: String?,
        q: String?,
    ): Cached<ApiList<EmployeeDto>> = RedisCache.getOrLoad(
        EmployeeCache.employeesList(limit, offset, departmentId, jobId, q),
    ) {
        EmployeeRepository.listEmployees(limit, offset, departmentId, jobId, q)
    }

    suspend fun findEmployee(id: Int): Cached<EmployeeDto?> =
        RedisCache.getOrLoadNullable(EmployeeCache.employee(id)) {
            EmployeeRepository.findEmployee(id)
        }

    suspend fun findEmployeeDetail(id: Int): Cached<EmployeeDetailDto?> =
        RedisCache.getOrLoadNullable(EmployeeCache.employeeDetails(id)) {
            EmployeeRepository.findEmployeeDetail(id)
        }

    suspend fun listEmployeeDetails(limit: Int, offset: Long): Cached<ApiList<EmployeeDetailDto>> =
        RedisCache.getOrLoad(EmployeeCache.empDetails(limit, offset)) {
            EmployeeRepository.listEmployeeDetails(limit, offset)
        }

    suspend fun updateEmployee(id: Int, patch: EmployeeUpdateRequest): EmployeeDto? = invalidateAfterWrite {
        EmployeeRepository.updateEmployee(id, patch)
    }

    suspend fun createEmployee(body: EmployeeCreateRequest): EmployeeDto = invalidateAfterWrite {
        EmployeeRepository.createEmployee(body)
    }

    suspend fun deleteEmployee(id: Int): Boolean = invalidateAfterWrite {
        EmployeeRepository.deleteEmployee(id) > 0
    }

    private suspend fun <T> invalidateAfterWrite(block: suspend () -> T): T = RedisCache.withInvalidation(
        EmployeeCache.EMPLOYEE_PREFIX,
        EmployeeCache.EMPLOYEES_PREFIX,
        EmployeeCache.EMP_DETAILS_PREFIX,
        OverviewCache.OVERVIEW,
        block = block,
    )
}
