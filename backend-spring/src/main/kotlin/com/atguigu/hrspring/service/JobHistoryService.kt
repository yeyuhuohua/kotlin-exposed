package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.JobHistoryDto
import com.atguigu.hrspring.entity.JobHistoryEntity
import com.atguigu.hrspring.mapper.JobHistoryMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobHistoryService(
    private val jobHistoryMapper: JobHistoryMapper,
) {
    @Cacheable(cacheNames = [CacheNames.JOB_HISTORY], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<JobHistoryDto> =
        jobHistoryMapper.selectList(
            QueryWrapper<JobHistoryEntity>().orderByAsc("employee_id", "start_date"),
        ).map { it.toDto() }
}

private fun JobHistoryEntity.toDto() = JobHistoryDto(
    employeeId = employeeId,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    jobId = jobId,
    departmentId = departmentId,
)
