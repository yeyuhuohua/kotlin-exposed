package com.atguigu.hr.demo.temp

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache
import com.atguigu.hr.overview.OverviewCache

/** 协调示例人员查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object TEmpService {
    /** 查询示例人员列表。 */
    suspend fun listTEmp(): Cached<List<TEmpDto>> =
        RedisCache.getOrLoad(TEmpCache.T_EMP) { TEmpRepository.listTEmp() }

    /** 更新示例人员。 */
    suspend fun updateTEmp(id: Int, patch: TEmpUpdateRequest): TEmpDto? = RedisCache.withInvalidation(
        TEmpCache.T_EMP,
        OverviewCache.OVERVIEW,
    ) {
        TEmpRepository.updateTEmp(id, patch)
    }

    /** 新增示例人员。 */
    suspend fun createTEmp(body: TEmpCreateRequest): TEmpDto = RedisCache.withInvalidation(
        TEmpCache.T_EMP,
        OverviewCache.OVERVIEW,
    ) {
        TEmpRepository.createTEmp(body)
    }
}
