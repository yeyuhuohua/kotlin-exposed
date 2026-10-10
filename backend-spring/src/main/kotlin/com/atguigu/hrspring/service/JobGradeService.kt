package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.JobGradeDto
import com.atguigu.hrspring.entity.JobGradeEntity
import com.atguigu.hrspring.mapper.JobGradeMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobGradeService(
    private val jobGradeMapper: JobGradeMapper,
    private val cache: RedisCache,
) {
    @Transactional(readOnly = true)
    fun list(): Pair<List<JobGradeDto>, Boolean> =
        cache.withCacheList(CacheNames.JOB_GRADES, JobGradeDto::class.java) {
            jobGradeMapper.selectList(QueryWrapper<JobGradeEntity>().orderByAsc("grade_level")).map { it.toDto() }
        }
}

private fun JobGradeEntity.toDto() = JobGradeDto(
    gradeLevel = gradeLevel,
    lowestSal = lowestSal,
    highestSal = highestSal,
)
