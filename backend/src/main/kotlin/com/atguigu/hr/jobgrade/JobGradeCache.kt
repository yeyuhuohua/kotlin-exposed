package com.atguigu.hr.jobgrade

/** 集中定义薪资等级缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object JobGradeCache {
    const val JOB_GRADES = "hr:job-grades"
}
