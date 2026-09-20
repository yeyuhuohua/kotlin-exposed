package com.atguigu.hr.jobgrade

import com.atguigu.hr.common.cache.Cached
import com.atguigu.hr.common.cache.RedisCache

/** 协调薪资等级查询与缓存，写操作使用统一失效入口避免返回旧数据。 */

object JobGradeService {
    suspend fun listJobGrades(): Cached<List<JobGradeDto>> =
        RedisCache.getOrLoad(JobGradeCache.JOB_GRADES) { JobGradeRepository.listJobGrades() }
}
