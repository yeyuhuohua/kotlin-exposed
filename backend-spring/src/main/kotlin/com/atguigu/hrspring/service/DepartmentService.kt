package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.api.Validation
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.DepartmentCreateRequest
import com.atguigu.hrspring.dto.DepartmentDto
import com.atguigu.hrspring.dto.DepartmentUpdateRequest
import com.atguigu.hrspring.entity.DepartmentEntity
import com.atguigu.hrspring.mapper.DepartmentMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DepartmentService(
    private val departmentMapper: DepartmentMapper,
    private val cache: RedisCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): Pair<List<DepartmentDto>, Boolean> =
        cache.withCacheList(CacheNames.DEPARTMENTS, DepartmentDto::class.java) {
            departmentMapper.selectList(QueryWrapper<DepartmentEntity>().orderByAsc("department_id"))
                .map { it.toDto() }
        }

    @Transactional(readOnly = true)
    fun find(id: Int): Pair<DepartmentDto, Boolean> =
        cache.withCache(CacheNames.department(id), DepartmentDto::class.java) {
            departmentMapper.selectById(id)?.toDto()
                ?: throw ApiException.notFound("department not found")
        }

    @Transactional
    fun create(body: DepartmentCreateRequest): DepartmentDto {
        validateCreate(body)
        val entity = DepartmentEntity().apply {
            departmentId = body.departmentId
            departmentName = body.departmentName
            managerId = body.managerId
            locationId = body.locationId
        }
        try {
            departmentMapper.insert(entity)
        } catch (e: DataIntegrityViolationException) {
            log.warn("department create conflict: {}", e.message)
            throw ApiException.conflict("create failed")
        }
        val created = departmentMapper.selectById(body.departmentId)?.toDto()
            ?: error("department insert missing row")
        cache.invalidateAfterCommit(listOf(CacheNames.DEPARTMENTS, CacheNames.OVERVIEW))
        return created
    }

    @Transactional
    fun update(id: Int, patch: DepartmentUpdateRequest): DepartmentDto {
        if (!patch.hasUpdates()) throw ApiException.badRequest("no fields to update")
        validateUpdate(patch)
        if (departmentMapper.selectById(id) == null) {
            throw ApiException.notFound("department not found")
        }
        val wrapper = UpdateWrapper<DepartmentEntity>().eq("department_id", id)
        patch.departmentName?.let { wrapper.set("department_name", it) }
        patch.managerId?.let { wrapper.set("manager_id", it) }
        patch.locationId?.let { wrapper.set("location_id", it) }
        try {
            departmentMapper.update(null, wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("department update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        val updated = departmentMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("department not found")
        cache.invalidateAfterCommit(
            listOf(CacheNames.DEPARTMENTS, CacheNames.department(id), CacheNames.OVERVIEW),
            listOf(CacheNames.EMPLOYEE_PREFIX, CacheNames.EMP_DETAILS_PREFIX),
        )
        return updated
    }

    private fun validateCreate(body: DepartmentCreateRequest) {
        if (body.departmentName.isBlank()) throw ApiException.badRequest("departmentName is required")
        Validation.requireMaxLength(body.departmentName, "departmentName", 30)
    }

    private fun validateUpdate(patch: DepartmentUpdateRequest) {
        if (patch.departmentName != null && patch.departmentName.isBlank()) {
            throw ApiException.badRequest("departmentName must not be blank")
        }
        Validation.requireMaxLength(patch.departmentName, "departmentName", 30)
    }
}

private fun DepartmentEntity.toDto() = DepartmentDto(
    departmentId = departmentId,
    departmentName = departmentName,
    managerId = managerId,
    locationId = locationId,
)
