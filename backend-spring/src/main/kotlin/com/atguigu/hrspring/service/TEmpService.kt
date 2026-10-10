package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.TEmpCreateRequest
import com.atguigu.hrspring.dto.TEmpDto
import com.atguigu.hrspring.dto.TEmpUpdateRequest
import com.atguigu.hrspring.entity.TEmpEntity
import com.atguigu.hrspring.mapper.TEmpMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 示例人物。整表一个列表键（不是 [CacheNames.T_EMP_PREFIX]）；deptId 是驼峰列。
 * 概览通过 [list] 取人数。
 */
@Service
class TEmpService(
    private val tEmpMapper: TEmpMapper,
    private val redisCache: RedisCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): Pair<List<TEmpDto>, Boolean> =
        redisCache.withCacheList(T_EMP_LIST, TEmpDto::class.java) {
            tEmpMapper.selectList(QueryWrapper<TEmpEntity>().orderByAsc("id")).map { it.toDto() }
        }

    @Transactional
    fun create(body: TEmpCreateRequest): TEmpDto {
        body.validate()
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
        val saved = reload(entity.id) ?: error("t_emp insert missing row")
        evict()
        return saved
    }

    @Transactional
    fun update(id: Int, patch: TEmpUpdateRequest): TEmpDto {
        if (!patch.hasUpdates()) {
            throw ApiException.badRequest("no fields to update")
        }
        patch.validate()
        if (lock(id) == null) throw ApiException.notFound("t_emp not found")
        val wrapper = UpdateWrapper<TEmpEntity>().eq("id", id)
        if (patch.name != null) wrapper.set("name", patch.name)
        if (patch.age != null) wrapper.set("age", patch.age)
        if (patch.deptId != null) wrapper.set("deptId", patch.deptId)
        if (patch.empno != null) wrapper.set("empno", patch.empno)
        try {
            tEmpMapper.update(wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("t_emp update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        val saved = reload(id) ?: error("t_emp update missing row")
        evict()
        return saved
    }

    /** selectList 走当前事务。selectOne 会另开 SqlSession，FOR UPDATE 锁不住后面的更新。 */
    private fun lock(id: Int): TEmpEntity? =
        tEmpMapper.selectList(QueryWrapper<TEmpEntity>().eq("id", id).last("FOR UPDATE")).firstOrNull()

    private fun reload(id: Int): TEmpDto? = tEmpMapper.selectById(id)?.toDto()

    private fun evict() {
        redisCache.invalidateAfterCommit(listOf(T_EMP_LIST, CacheNames.OVERVIEW))
    }

    private fun TEmpEntity.toDto() = TEmpDto(
        id = id,
        name = name,
        age = age,
        deptId = deptId,
        empno = empno,
    )

    private companion object {
        /** CacheNames.T_EMP_PREFIX 是 hrs:t-emp:，整表列表不带末尾冒号。 */
        const val T_EMP_LIST = "hrs:t-emp"
    }
}
