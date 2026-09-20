package com.atguigu.hr.overview

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.demo.temp.TEmpService
import com.atguigu.hr.department.DepartmentService
import com.atguigu.hr.employee.EmployeeService
import com.atguigu.hr.geography.GeographyService
import com.atguigu.hr.job.JobService
import com.atguigu.hr.location.LocationService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 概览是聚合接口：跨模块读取统一走对方的 Service，聚合统计留在数据库里做。 */
object OverviewService {
    /**
     * 八路 async 并行，各自独立 suspendTransaction。
     * 不能把 Flow 抛出事务再收集，因此每路在内部 toList。
     */
    suspend fun loadOverview(): Cached<OverviewDto> =
        RedisCache.getOrLoad(OverviewCache.OVERVIEW) { loadFromDb() }

    private suspend fun loadFromDb(): OverviewDto = coroutineScope {
        val started = System.nanoTime()
        val employeesDeferred = async {
            EmployeeService.listEmployees(limit = 5, offset = 0, departmentId = null, jobId = null, q = null)
        }
        val headcountDeferred = async { EmployeeService.countByDepartment() }
        val salaryDeferred = async { EmployeeService.salarySummary() }
        val departmentsDeferred = async { DepartmentService.listDepartments() }
        val jobsDeferred = async { JobService.listJobs() }
        val locationsDeferred = async { LocationService.listLocations() }
        val regionsDeferred = async { GeographyService.listRegions() }
        val tEmpDeferred = async { TEmpService.listTEmp() }

        val employees = employeesDeferred.await()
        val headcount = headcountDeferred.await()
        val salary = salaryDeferred.await()
        val departments = departmentsDeferred.await()
        val jobs = jobsDeferred.await()
        val locations = locationsDeferred.await()
        val regions = regionsDeferred.await()
        val tEmp = tEmpDeferred.await()

        OverviewDto(
            fetchedWith = "kotlinx.coroutines.async",
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
            employeeTotal = employees.value.total,
            departmentCount = departments.value.size,
            jobCount = jobs.value.size,
            locationCount = locations.value.size,
            regionCount = regions.value.size,
            tEmpCount = tEmp.value.size,
            sampleEmployees = employees.value.items,
            departments = departments.value,
            regions = regions.value,
            departmentHeadcount = headcount,
            salarySummary = salary,
        )
    }
}
