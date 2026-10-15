package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.dto.TEmpCreateRequest
import com.atguigu.hrspring.dto.TEmpDto
import com.atguigu.hrspring.dto.TEmpUpdateRequest
import com.atguigu.hrspring.entity.TEmpEntity
import com.atguigu.hrspring.mapper.TEmpMapper
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

/**
 * 示例人物。整表一个缓存项；deptId 是驼峰列。概览通过 [list] 取人数。
 */
@Validated
@Service
class TEmpService(
    private val tEmpMapper: TEmpMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Cacheable(cacheNames = [CacheNames.T_EMP], key = "'all'")
    @Transactional(readOnly = true)
    fun list(): List<TEmpDto> =
        tEmpMapper.selectList(KtQueryWrapper(TEmpEntity::class.java).orderByAsc(TEmpEntity::id)).map { it.toDto() }

    @CacheEvict(cacheNames = [CacheNames.T_EMP, CacheNames.OVERVIEW], allEntries = true)
    @Transactional
    fun create(@Valid body: TEmpCreateRequest): TEmpDto {
        val entity = TEmpEntity().apply {
            name = body.name
            age = body.age
            deptId = body.deptId
            empno = body.empno
        }
        try {
            tEmpMapper.insert(entity)
        } catch (e: DataIntegrityViolationException) {
            log.warn("t_emp create conflict: {}", e.message)
            throw ApiException.conflict("create failed")
        }
        return reload(checkNotNull(entity.id) { "t_emp insert missing generated id" })
            ?: error("t_emp insert missing row")
    }

    @CacheEvict(cacheNames = [CacheNames.T_EMP, CacheNames.OVERVIEW], allEntries = true)
    @Transactional
    fun update(id: Int, @Valid patch: TEmpUpdateRequest): TEmpDto {
        if (lock(id) == null) throw ApiException.notFound("t_emp not found")
        val wrapper = KtUpdateWrapper(TEmpEntity::class.java).eq(TEmpEntity::id, id)
        if (patch.name != null) wrapper.set(TEmpEntity::name, patch.name)
        if (patch.age != null) wrapper.set(TEmpEntity::age, patch.age)
        if (patch.deptId != null) wrapper.set(TEmpEntity::deptId, patch.deptId)
        if (patch.empno != null) wrapper.set(TEmpEntity::empno, patch.empno)
        try {
            tEmpMapper.update(wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("t_emp update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        return reload(id) ?: error("t_emp update missing row")
    }

    /** selectList 走当前事务。selectOne 会另开 SqlSession，FOR UPDATE 锁不住后面的更新。 */
    private fun lock(id: Int): TEmpEntity? =
        tEmpMapper.selectList(
            KtQueryWrapper(TEmpEntity::class.java).eq(TEmpEntity::id, id).last("FOR UPDATE"),
        ).firstOrNull()

    private fun reload(id: Int): TEmpDto? = tEmpMapper.selectById(id)?.toDto()

    private fun TEmpEntity.toDto() = TEmpDto(
        id = checkNotNull(id),
        name = name,
        age = age,
        deptId = deptId,
        empno = empno,
    )
}
