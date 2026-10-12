package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.JobGradeDto
import com.atguigu.hrspring.entity.JobGradeEntity
import com.atguigu.hrspring.mapper.JobGradeMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobGradeService(
    private val jobGradeMapper: JobGradeMapper,
) {
    @Cacheable(cacheNames = [CacheNames.JOB_GRADES], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<JobGradeDto> =
        jobGradeMapper.selectList(QueryWrapper<JobGradeEntity>().orderByAsc("grade_level")).map { it.toDto() }
}

private fun JobGradeEntity.toDto() = JobGradeDto(
    gradeLevel = gradeLevel,
    lowestSal = lowestSal,
    highestSal = highestSal,
)
