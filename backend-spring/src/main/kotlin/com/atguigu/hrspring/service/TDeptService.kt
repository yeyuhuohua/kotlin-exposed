package com.atguigu.hrspring.service
import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.common.cache.CacheNames
import com.atguigu.hrspring.common.cache.RedisCache
import com.atguigu.hrspring.dto.TDeptCreateRequest
import com.atguigu.hrspring.dto.TDeptDto
import com.atguigu.hrspring.dto.TDeptUpdateRequest
import com.atguigu.hrspring.entity.TDeptEntity
import com.atguigu.hrspring.mapper.TDeptMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 示例门派。deptName 是驼峰列；写入后失效本表列表和概览。 */
@Service
class TDeptService(
    private val tDeptMapper: TDeptMapper,
    private val redisCache: RedisCache,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): Pair<List<TDeptDto>, Boolean> =
        redisCache.withCacheList(CacheNames.T_DEPT, TDeptDto::class.java) {
            tDeptMapper.selectList(QueryWrapper<TDeptEntity>().orderByAsc("id")).map { it.toDto() }
        }

    @Transactional
    fun create(body: TDeptCreateRequest): TDeptDto {
        if (!body.hasValues()) {
            throw ApiException.badRequest("deptName or address is required")
        }
        body.validate()
        val entity = TDeptEntity().apply {
            deptName = body.deptName
            address = body.address
        }
        try {
            tDeptMapper.insert(entity)
        } catch (e: DataIntegrityViolationException) {
            log.warn("t_dept create conflict: {}", e.message)
            throw ApiException.conflict("create failed")
        }
        val saved = reload(entity.id) ?: error("t_dept insert missing row")
        evict()
        return saved
    }

    @Transactional
    fun update(id: Int, patch: TDeptUpdateRequest): TDeptDto {
        if (!patch.hasUpdates()) {
            throw ApiException.badRequest("no fields to update")
        }
        patch.validate()
        if (lock(id) == null) throw ApiException.notFound("t_dept not found")
        val wrapper = UpdateWrapper<TDeptEntity>().eq("id", id)
        if (patch.deptName != null) wrapper.set("deptName", patch.deptName)
        if (patch.address != null) wrapper.set("address", patch.address)
        try {
            tDeptMapper.update(wrapper)
        } catch (e: DataIntegrityViolationException) {
            log.warn("t_dept update conflict: {}", e.message)
            throw ApiException.conflict("update failed")
        }
        val saved = reload(id) ?: error("t_dept update missing row")
        evict()
        return saved
    }

    /** selectList 走当前事务。selectOne 会另开 SqlSession，FOR UPDATE 锁不住后面的更新。 */
    private fun lock(id: Int): TDeptEntity? =
        tDeptMapper.selectList(QueryWrapper<TDeptEntity>().eq("id", id).last("FOR UPDATE")).firstOrNull()

    private fun reload(id: Int): TDeptDto? = tDeptMapper.selectById(id)?.toDto()

    private fun evict() {
        redisCache.invalidateAfterCommit(listOf(CacheNames.T_DEPT, CacheNames.OVERVIEW))
    }

    private fun TDeptEntity.toDto() = TDeptDto(
        id = id,
        deptName = deptName,
        address = address,
    )
}
