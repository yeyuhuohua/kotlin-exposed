package com.atguigu.hrspring.common.api

import com.atguigu.hrspring.common.cache.CacheHitMarker
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import org.springframework.http.ResponseEntity

/** 解析分页参数，并限制每页条数和偏移量的合法范围。 */
fun pageParams(limit: Int?, offset: Long?): PageParams = PageParams(limit ?: 50, offset ?: 0)

/**
 * 用同一组条件分别计数和分页。limit/offset 已由 PageParams 校验成整数，拼进 LIMIT 是安全的。
 */
inline fun <reified E : Any> BaseMapper<E>.selectPage(page: PageParams, build: KtQueryWrapper<E>.() -> Unit): ApiList<E> {
    val total = selectCount(KtQueryWrapper(E::class.java).apply(build))
    val items = selectList(
        KtQueryWrapper(E::class.java).apply(build).last("LIMIT ${page.limit} OFFSET ${page.offset}"),
    )
    return ApiList(total, items)
}

/** hit 省略时读取本请求第一次 Spring Cache 查询的结果。显式传入则不再看标记。 */
fun <T> cachedOk(data: T, hit: Boolean? = null, message: String = "ok"): ResponseEntity<ApiResult<T>> =
    ResponseEntity.ok()
        .header("X-Cache", if (hit ?: CacheHitMarker.current()) "HIT" else "MISS")
        .body(ApiResult(200, message, data))

/** 把业务数据包装成统一响应并设置缓存响应头。 */
fun <T> body(data: T, message: String): ResponseEntity<ApiResult<T>> =
    ResponseEntity.ok(ApiResult(200, message, data))
