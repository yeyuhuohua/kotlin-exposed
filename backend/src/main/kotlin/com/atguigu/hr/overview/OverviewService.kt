package com.atguigu.hr.overview

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.demo.temp.TEmpRepository
import com.atguigu.hr.department.DepartmentRepository
import com.atguigu.hr.employee.EmployeeRepository
import com.atguigu.hr.geography.GeographyRepository
import com.atguigu.hr.job.JobRepository
import com.atguigu.hr.location.LocationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object OverviewService {
    /**
     * 六路 async 并行查库，各自独立 suspendTransaction。
     * 不能把 Flow 抛出事务再收集，因此每路在内部 toList。
     */
    suspend fun loadOverview(): Cached<OverviewDto> =
        RedisCache.getOrLoad(OverviewCache.OVERVIEW) { loadFromDb() }

    private suspend fun loadFromDb(): OverviewDto = coroutineScope {
        val started = System.nanoTime()
        val employeesDeferred = async {
            EmployeeRepository.listEmployees(limit = 5, offset = 0, departmentId = null, jobId = null, q = null)
        }
        val departmentsDeferred = async { DepartmentRepository.listDepartments() }
        val jobsDeferred = async { JobRepository.listJobs() }
        val locationsDeferred = async { LocationRepository.listLocations() }
        val regionsDeferred = async { GeographyRepository.listRegions() }
        val tEmpDeferred = async { TEmpRepository.listTEmp() }

        val employees = employeesDeferred.await()
        val departments = departmentsDeferred.await()
        val jobs = jobsDeferred.await()
        val locations = locationsDeferred.await()
        val regions = regionsDeferred.await()
        val tEmp = tEmpDeferred.await()

        OverviewDto(
            fetchedWith = "kotlinx.coroutines.async",
            elapsedMs = (System.nanoTime() - started) / 1_000_000,
            employeeTotal = employees.total,
            departmentCount = departments.size,
            jobCount = jobs.size,
            locationCount = locations.size,
            regionCount = regions.size,
            tEmpCount = tEmp.size,
            sampleEmployees = employees.items,
            departments = departments,
            regions = regions,
        )
    }
}
