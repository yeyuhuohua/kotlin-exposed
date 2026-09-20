package com.atguigu.hr.common.api

import kotlinx.serialization.Serializable

/** 分页列表。 */
@Serializable
data class ApiList<T>(
    val total: Long,
    val items: List<T>,
)
