package com.atguigu.hr.department

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义部门缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object DepartmentCache {
    const val DEPARTMENTS = "hr:departments"
    fun department(id: Int) = "hr:department:$id"

    suspend fun evict(id: Int) {
        RedisCache.evict(department(id))
    }

    suspend fun evictList() {
        RedisCache.evict(DEPARTMENTS)
    }
}
