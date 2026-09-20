package com.atguigu.hr.location

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义办公地点缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object LocationCache {
    const val LOCATIONS = "hr:locations"

    suspend fun evictList() {
        RedisCache.evict(LOCATIONS)
    }
}
