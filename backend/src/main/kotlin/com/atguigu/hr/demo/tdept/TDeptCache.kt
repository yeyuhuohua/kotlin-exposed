package com.atguigu.hr.demo.tdept

import com.atguigu.hr.common.cache.RedisCache

/** 集中定义示例部门缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object TDeptCache {
    const val T_DEPT = "hr:t-dept"

    suspend fun evictList() {
        RedisCache.evict(T_DEPT)
    }
}
