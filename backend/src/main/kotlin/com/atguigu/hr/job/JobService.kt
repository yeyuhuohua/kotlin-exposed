package com.atguigu.hr.job

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.employee.EmployeeCache
import com.atguigu.hr.overview.OverviewCache

/** 协调岗位查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object JobService {
    /** 查询岗位列表。 */
    suspend fun listJobs(): Cached<List<JobDto>> =
        RedisCache.getOrLoad(JobCache.JOBS) { JobRepository.listJobs() }

    /** 更新岗位。 */
    suspend fun updateJob(jobId: String, patch: JobUpdateRequest): JobDto? = RedisCache.withInvalidation(
        JobCache.JOBS,
        EmployeeCache.EMPLOYEE_PREFIX,
        EmployeeCache.EMP_DETAILS_PREFIX,
        OverviewCache.OVERVIEW,
    ) {
        JobRepository.updateJob(jobId, patch)
    }

    /** 新增岗位。 */
    suspend fun createJob(body: JobCreateRequest): JobDto = RedisCache.withInvalidation(
        JobCache.JOBS,
        OverviewCache.OVERVIEW,
    ) {
        JobRepository.createJob(body)
    }
}
