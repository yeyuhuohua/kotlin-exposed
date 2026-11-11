package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.JobGradeDto
import com.atguigu.hrspring.entity.JobGradeEntity
import com.atguigu.hrspring.mapper.JobGradeMapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobGradeService(
    private val jobGradeMapper: JobGradeMapper,
) {
    /** 查询薪资等级列表。 */
    @Cacheable(cacheNames = [CacheNames.JOB_GRADES], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<JobGradeDto> =
        jobGradeMapper.selectList(KtQueryWrapper(JobGradeEntity::class.java).orderByAsc(JobGradeEntity::gradeLevel)).map { it.toDto() }
}

/** 将薪资等级数据转换为对外传输对象。 */
private fun JobGradeEntity.toDto() = JobGradeDto(
    gradeLevel = gradeLevel,
    lowestSal = lowestSal,
    highestSal = highestSal,
)
