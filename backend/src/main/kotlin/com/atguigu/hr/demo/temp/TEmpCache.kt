package com.atguigu.hr.demo.temp

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义示例人员缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object TEmpCache {
    const val T_EMP = "hr:t-emp"

    suspend fun evictList() {
        RedisCache.evict(T_EMP)
    }
}
