package com.atguigu.hr.job

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义岗位缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object JobCache {
    const val JOBS = "hr:jobs"

    suspend fun evictList() {
        RedisCache.evict(JOBS)
    }
}
