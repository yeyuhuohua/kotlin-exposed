package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.DepartmentCreateRequest
import com.atguigu.hrspring.dto.DepartmentDto
import com.atguigu.hrspring.dto.DepartmentUpdateRequest
import com.atguigu.hrspring.entity.DepartmentEntity
import com.atguigu.hrspring.mapper.DepartmentMapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtUpdateWrapper
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Validated
@Service
class DepartmentService(
    private val departmentMapper: DepartmentMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(cacheNames = [CacheNames.DEPARTMENTS], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<DepartmentDto> =
        departmentMapper.selectList(KtQueryWrapper(DepartmentEntity::class.java).orderByAsc(DepartmentEntity::departmentId))
            .map { it.toDto() }

    @Cacheable(cacheNames = [CacheNames.DEPARTMENT], key = "#id")
    @Transactional(readOnly = true)
    fun find(id: Int): DepartmentDto =
        departmentMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("department not found")

    @CacheEvict(
        cacheNames = [CacheNames.DEPARTMENTS, CacheNames.OVERVIEW],
        allEntries = true,
    )
    @Transactional
    fun create(@Valid body: DepartmentCreateRequest): DepartmentDto {
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
        return departmentMapper.selectById(body.departmentId)?.toDto()
            ?: error("department insert missing row")
    }

    @CacheEvict(
        cacheNames = [
            CacheNames.DEPARTMENTS,
            CacheNames.DEPARTMENT,
            CacheNames.EMPLOYEE,
            CacheNames.EMPLOYEE_DETAILS,
            CacheNames.EMP_DETAILS,
            CacheNames.OVERVIEW,
        ],
        allEntries = true,
    )
    @Transactional
    fun update(id: Int, @Valid patch: DepartmentUpdateRequest): DepartmentDto {
        if (departmentMapper.selectById(id) == null) {
            throw ApiException.notFound("department not found")
        }
        val wrapper = KtUpdateWrapper(DepartmentEntity::class.java).eq(DepartmentEntity::departmentId, id)
        patch.departmentName?.let { wrapper.set(DepartmentEntity::departmentName, it) }
        patch.managerId?.let { wrapper.set(DepartmentEntity::managerId, it) }
        patch.locationId?.let { wrapper.set(DepartmentEntity::locationId, it) }
        try {
            departmentMapper.update(null, wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("department update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        return departmentMapper.selectById(id)?.toDto()
            ?: throw ApiException.notFound("department not found")
    }
}

private fun DepartmentEntity.toDto() = DepartmentDto(
    departmentId = checkNotNull(departmentId),
    departmentName = departmentName,
    managerId = managerId,
    locationId = locationId,
)
