package com.atguigu.hr.department

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义部门缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object DepartmentCache {
    const val DEPARTMENTS = "hr:departments"
    /** 返回单部门缓存使用的 Redis 键。 */
    fun department(id: Int) = "hr:department:$id"

    /** 使指定缓存项失效；缓存不可用时按旁路策略处理。 */
    suspend fun evict(id: Int) {
        RedisCache.evict(department(id))
    }

    /** 清除全部部门列表缓存。 */
    suspend fun evictList() {
        RedisCache.evict(DEPARTMENTS)
    }
}
