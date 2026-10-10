package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.Validation
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.JobCreateRequest
import com.atguigu.hrspring.dto.JobDto
import com.atguigu.hrspring.dto.JobUpdateRequest
import com.atguigu.hrspring.entity.JobEntity
import com.atguigu.hrspring.mapper.JobMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JobService(
    private val jobMapper: JobMapper,
    private val cache: RedisCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): Pair<List<JobDto>, Boolean> =
        cache.withCacheList(CacheNames.JOBS, JobDto::class.java) {
            jobMapper.selectList(QueryWrapper<JobEntity>().orderByAsc("job_id")).map { it.toDto() }
        }

    @Transactional
    fun create(body: JobCreateRequest): JobDto {
        validateCreate(body)
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
        val created = jobMapper.selectById(body.jobId)?.toDto() ?: error("job insert missing row")
        cache.invalidateAfterCommit(listOf(CacheNames.JOBS, CacheNames.OVERVIEW))
        return created
    }

    @Transactional
    fun update(jobId: String, patch: JobUpdateRequest): JobDto {
        if (jobId.isBlank()) throw ApiException.badRequest("invalid job id")
        if (!patch.hasUpdates()) throw ApiException.badRequest("no fields to update")
        validateUpdate(patch)
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
        val updated = jobMapper.selectById(jobId)?.toDto() ?: error("job update missing row")
        cache.invalidateAfterCommit(
            listOf(CacheNames.JOBS, CacheNames.OVERVIEW),
            listOf(CacheNames.EMPLOYEE_PREFIX, CacheNames.EMP_DETAILS_PREFIX),
        )
        return updated
    }

    private fun validateCreate(body: JobCreateRequest) {
        if (body.jobId.isBlank() || body.jobTitle.isBlank()) {
            throw ApiException.badRequest("jobId and jobTitle are required")
        }
        if (body.jobId == "." || body.jobId == "..") {
            throw ApiException.badRequest("jobId must not be '.' or '..'")
        }
        Validation.requireMaxLength(body.jobId, "jobId", 10)
        Validation.requireMaxLength(body.jobTitle, "jobTitle", 35)
        if (body.minSalary != null && body.minSalary < 0) {
            throw ApiException.badRequest("minSalary must be non-negative")
        }
        if (body.maxSalary != null && body.maxSalary < 0) {
            throw ApiException.badRequest("maxSalary must be non-negative")
        }
        if (body.minSalary != null && body.maxSalary != null && body.minSalary > body.maxSalary) {
            throw ApiException.badRequest("minSalary must not exceed maxSalary")
        }
    }

    private fun validateUpdate(patch: JobUpdateRequest) {
        if (patch.jobTitle != null && patch.jobTitle.isBlank()) {
            throw ApiException.badRequest("jobTitle must not be blank")
        }
        Validation.requireMaxLength(patch.jobTitle, "jobTitle", 35)
        if (patch.minSalary != null && patch.minSalary < 0) {
            throw ApiException.badRequest("minSalary must be non-negative")
        }
        if (patch.maxSalary != null && patch.maxSalary < 0) {
            throw ApiException.badRequest("maxSalary must be non-negative")
        }
    }
}

private fun JobEntity.toDto() = JobDto(
    jobId = jobId,
    jobTitle = jobTitle,
    minSalary = minSalary,
    maxSalary = maxSalary,
)
