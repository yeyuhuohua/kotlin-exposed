package com.atguigu.hr.common.cache

/** 一次缓存读取结果。hit=true 表示 Redis 命中，未走数据库。 */
data class Cached<T>(
    val value: T,
    val hit: Boolean,
)
