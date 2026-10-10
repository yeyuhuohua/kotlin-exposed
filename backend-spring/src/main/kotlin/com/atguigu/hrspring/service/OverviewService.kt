package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.PageParams
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.OverviewDto
import org.springframework.stereotype.Service

/**
 * 概览只通过各模块 Service 读数，不注入别人的 Mapper。
 * 在请求线程上顺序组装：CurrentUser 放在 ThreadLocal 里，不能拆到别的线程。
 */
@Service
class OverviewService(
    private val employeeService: EmployeeService,
    private val departmentService: DepartmentService,
    private val jobService: JobService,
    private val locationService: LocationService,
    private val geographyService: GeographyService,
    private val tEmpService: TEmpService,
    private val cache: RedisCache,
) {
    fun load(): Pair<OverviewDto, Boolean> =
        cache.withCache(CacheNames.OVERVIEW, OverviewDto::class.java) {
            val started = System.nanoTime()
            val employees = employeeService.list(PageParams(5, 0), null, null, null, false).first
            val headcount = employeeService.countByDepartment()
            val salary = employeeService.salarySummary()
            val departments = departmentService.list().first
            val jobs = jobService.list().first
            val locations = locationService.list().first
            val regions = geographyService.listRegions().first
            val tEmp = tEmpService.list().first
            OverviewDto(
                fetchedWith = "mybatis-plus",
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
                departmentHeadcount = headcount,
                salarySummary = salary,
            )
        }
}
