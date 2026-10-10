package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.JobHistoryDto
import com.atguigu.hrspring.entity.JobHistoryEntity
import com.atguigu.hrspring.mapper.JobHistoryMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobHistoryService(
    private val jobHistoryMapper: JobHistoryMapper,
    private val cache: RedisCache,
) {
    @Transactional(readOnly = true)
    fun list(): Pair<List<JobHistoryDto>, Boolean> =
        cache.withCacheList(CacheNames.JOB_HISTORY_PREFIX + "all", JobHistoryDto::class.java) {
            jobHistoryMapper.selectList(
                QueryWrapper<JobHistoryEntity>().orderByAsc("employee_id", "start_date"),
            ).map { it.toDto() }
        }
}

private fun JobHistoryEntity.toDto() = JobHistoryDto(
    employeeId = employeeId,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    jobId = jobId,
    departmentId = departmentId,
)
