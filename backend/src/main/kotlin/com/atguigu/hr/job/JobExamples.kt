package com.atguigu.hr.job

/** 仅供 OpenAPI 文档展示的岗位样例，不参与数据库初始化或业务计算。 */

internal val sampleJob = JobDto("AD_PRES", "President", 20080, 40000)
internal val sampleJobPatch = JobUpdateRequest(
    jobTitle = "President",
    minSalary = 20080,
    maxSalary = 40000,
)
internal val sampleJobCreate = JobCreateRequest(
    jobId = "KT_DEV",
    jobTitle = "Kotlin Developer",
    minSalary = 4000,
    maxSalary = 9000,
)
