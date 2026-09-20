package com.atguigu.hr.demo.tdept

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache

/** 协调示例部门查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object TDeptService {
    suspend fun listTDept(): Cached<List<TDeptDto>> =
        RedisCache.getOrLoad(TDeptCache.T_DEPT) { TDeptRepository.listTDept() }

    suspend fun updateTDept(id: Int, patch: TDeptUpdateRequest): TDeptDto? = RedisCache.withInvalidation(TDeptCache.T_DEPT) {
        TDeptRepository.updateTDept(id, patch)
    }

    suspend fun createTDept(body: TDeptCreateRequest): TDeptDto = RedisCache.withInvalidation(TDeptCache.T_DEPT) {
        TDeptRepository.createTDept(body)
    }
}
