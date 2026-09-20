package com.atguigu.hr.jobhistory

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache

/** 协调任职历史查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object JobHistoryService {
    suspend fun listJobHistory(): Cached<List<JobHistoryDto>> =
        RedisCache.getOrLoad(JobHistoryCache.JOB_HISTORY) { JobHistoryRepository.listJobHistory() }
}
