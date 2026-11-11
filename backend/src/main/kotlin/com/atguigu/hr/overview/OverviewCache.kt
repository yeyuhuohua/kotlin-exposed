package com.atguigu.hr.overview

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义概览缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object OverviewCache {
    const val OVERVIEW = "hr:overview"

    /** 使指定缓存项失效；缓存不可用时按旁路策略处理。 */
    suspend fun evict() {
        RedisCache.evict(OVERVIEW)
    }
}
