package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.JobCreateRequest
import com.atguigu.hrspring.dto.JobDto
import com.atguigu.hrspring.dto.JobUpdateRequest
import com.atguigu.hrspring.entity.JobEntity
import com.atguigu.hrspring.mapper.JobMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Validated
@Service
class JobService(
    private val jobMapper: JobMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(cacheNames = [CacheNames.JOBS], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<JobDto> =
        jobMapper.selectList(QueryWrapper<JobEntity>().orderByAsc("job_id")).map { it.toDto() }

    @CacheEvict(cacheNames = [CacheNames.JOBS, CacheNames.OVERVIEW], allEntries = true)
    @Transactional
    fun create(@Valid body: JobCreateRequest): JobDto {
        val entity = JobEntity().apply {
            jobId = body.jobId
            jobTitle = body.jobTitle
            minSalary = body.minSalary
            maxSalary = body.maxSalary
        }
        try {
            jobMapper.insert(entity)
        } catch (e: DataIntegrityViolationException) {
            log.warn("job create conflict: {}", e.message)
            throw ApiException.conflict("create failed")
        }
        return jobMapper.selectById(body.jobId)?.toDto() ?: error("job insert missing row")
    }

    @CacheEvict(
        cacheNames = [
            CacheNames.JOBS,
            CacheNames.EMPLOYEE,
            CacheNames.EMPLOYEE_DETAILS,
            CacheNames.EMP_DETAILS,
            CacheNames.OVERVIEW,
        ],
        allEntries = true,
    )
    @Transactional
    fun update(@NotBlank(message = "invalid job id") jobId: String, @Valid patch: JobUpdateRequest): JobDto {
        val current = jobMapper.selectOne(
            QueryWrapper<JobEntity>().eq("job_id", jobId).last("FOR UPDATE"),
        ) ?: throw ApiException.notFound("job not found")
        val min = patch.minSalary ?: current.minSalary
        val max = patch.maxSalary ?: current.maxSalary
        if (min != null && max != null && min > max) {
            throw ApiException.badRequest("minSalary must not exceed maxSalary")
        }
        val wrapper = UpdateWrapper<JobEntity>().eq("job_id", jobId)
        patch.jobTitle?.let { wrapper.set("job_title", it) }
        patch.minSalary?.let { wrapper.set("min_salary", it) }
        patch.maxSalary?.let { wrapper.set("max_salary", it) }
        try {
            jobMapper.update(null, wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("job update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        return jobMapper.selectById(jobId)?.toDto() ?: error("job update missing row")
    }
}

private fun JobEntity.toDto() = JobDto(
    jobId = jobId,
    jobTitle = jobTitle,
    minSalary = minSalary,
    maxSalary = maxSalary,
)
