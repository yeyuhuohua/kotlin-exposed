package com.atguigu.hr.geography

/** 集中定义国家与区域缓存键，避免路由或跨模块调用自行拼接 Redis 命名。 */

object GeographyCache {
    const val COUNTRIES = "hr:countries"
    const val REGIONS = "hr:regions"
}
