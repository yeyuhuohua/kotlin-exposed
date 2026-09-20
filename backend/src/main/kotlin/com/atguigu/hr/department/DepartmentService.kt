package com.atguigu.hr.department

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.employee.EmployeeCache
import com.atguigu.hr.employee.EmployeeDto
import com.atguigu.hr.employee.EmployeeService
import com.atguigu.hr.overview.OverviewCache

/** 协调部门查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object DepartmentService {
    suspend fun listDepartments(): Cached<List<DepartmentDto>> =
        RedisCache.getOrLoad(DepartmentCache.DEPARTMENTS) { DepartmentRepository.listDepartments() }

    suspend fun findDepartment(id: Int): Cached<DepartmentDto?> =
        RedisCache.getOrLoadNullable(DepartmentCache.department(id)) { DepartmentRepository.findDepartment(id) }

    /** 员工数据属于员工模块，这里只转发，缓存与失效规则由对方维护。 */
    suspend fun listEmployeesByDepartment(departmentId: Int): Cached<List<EmployeeDto>> =
        EmployeeService.listEmployeesByDepartment(departmentId)

    suspend fun updateDepartment(id: Int, patch: DepartmentUpdateRequest): DepartmentDto? = RedisCache.withInvalidation(
        DepartmentCache.DEPARTMENTS,
        EmployeeCache.EMPLOYEE_PREFIX,
        EmployeeCache.EMP_DETAILS_PREFIX,
        OverviewCache.OVERVIEW,
    ) {
        DepartmentRepository.updateDepartment(id, patch)
    }

    suspend fun createDepartment(body: DepartmentCreateRequest): DepartmentDto = RedisCache.withInvalidation(
        DepartmentCache.DEPARTMENTS,
        OverviewCache.OVERVIEW,
    ) {
        DepartmentRepository.createDepartment(body)
    }
}
