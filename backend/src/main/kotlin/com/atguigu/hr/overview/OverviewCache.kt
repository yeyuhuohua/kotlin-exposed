package com.atguigu.hr.overview

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义概览缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object OverviewCache {
    const val OVERVIEW = "hr:overview"

    suspend fun evict() {
        RedisCache.evict(OVERVIEW)
    }
}
