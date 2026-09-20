package com.atguigu.hr.jobhistory

/** 集中定义任职历史缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object JobHistoryCache {
    const val JOB_HISTORY = "hr:job-history"
}
