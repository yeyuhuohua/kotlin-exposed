package com.atguigu.hrspring.common.api

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.springframework.http.ResponseEntity

fun pageParams(limit: Int?, offset: Long?): PageParams = PageParams(limit ?: 50, offset ?: 0)

/**
 * 用同一组条件分别计数和分页。limit/offset 已由 PageParams 校验成整数，拼进 LIMIT 是安全的。
 */
fun <E> BaseMapper<E>.selectPage(page: PageParams, build: QueryWrapper<E>.() -> Unit): ApiList<E> {
    val total = selectCount(QueryWrapper<E>().apply(build))
    val items = selectList(
        QueryWrapper<E>().apply(build).last("LIMIT ${page.limit} OFFSET ${page.offset}"),
    )
    return ApiList(total, items)
}

fun <T> cachedOk(data: T, hit: Boolean, message: String = "ok"): ResponseEntity<ApiResult<T>> =
    ResponseEntity.ok()
        .header("X-Cache", if (hit) "HIT" else "MISS")
        .body(ApiResult(200, message, data))

fun <T> body(data: T, message: String): ResponseEntity<ApiResult<T>> =
    ResponseEntity.ok(ApiResult(200, message, data))
