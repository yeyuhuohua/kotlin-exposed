package com.atguigu.hrspring.service
import com.atguigu.hrspring.dto.ApiCallRecordDto
import com.atguigu.hrspring.dto.LoginRecordDto
import com.atguigu.hrspring.entity.AuditApiCallEntity
import com.atguigu.hrspring.entity.AuditLoginRecordEntity
import com.atguigu.hrspring.mapper.AuditApiCallMapper
import com.atguigu.hrspring.mapper.AuditLoginRecordMapper
import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.LikeEscape
import com.atguigu.hrspring.common.api.pageParams
import com.atguigu.hrspring.common.api.selectPage
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 审计查询实时读库，不进 Redis。写入仍走 audit.AuditService。 */
@Service
class AuditQueryService(
    private val loginRecordMapper: AuditLoginRecordMapper,
    private val apiCallMapper: AuditApiCallMapper,
) {
    @Transactional(readOnly = true)
    fun listLogins(
        limit: Int?,
        offset: Long?,
        username: String?,
        success: Boolean?,
    ): ApiList<LoginRecordDto> {
        val page = pageParams(limit, offset)
        val rows = loginRecordMapper.selectPage(page) {
            containsLike("username", username)
            if (success != null) {
                eq("success", success)
            }
            orderByDesc("id")
        }
        return ApiList(
            total = rows.total,
            items = rows.items.map { it.toDto() },
        )
    }

    @Transactional(readOnly = true)
    fun listApiCalls(
        limit: Int?,
        offset: Long?,
        username: String?,
        method: String?,
        path: String?,
    ): ApiList<ApiCallRecordDto> {
        val page = pageParams(limit, offset)
        val rows = apiCallMapper.selectPage(page) {
            containsLike("username", username)
            val normalizedMethod = method?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
            if (normalizedMethod != null) {
                eq("method", normalizedMethod)
            }
            prefixLike("path", path)
            orderByDesc("id")
        }
        return ApiList(
            total = rows.total,
            items = rows.items.map { it.toDto() },
        )
    }

    private fun AuditLoginRecordEntity.toDto() = LoginRecordDto(
        id = id,
        username = username,
        userId = userId,
        ip = ip,
        userAgent = userAgent,
        success = success,
        errorCode = errorCode,
        createdAt = createdAt.toString(),
    )

    private fun AuditApiCallEntity.toDto() = ApiCallRecordDto(
        id = id,
        userId = userId,
        username = username,
        method = method,
        path = path,
        queryString = queryString,
        statusCode = statusCode,
        durationMs = durationMs,
        ip = ip,
        userAgent = userAgent,
        createdAt = createdAt.toString(),
    )
}

/** LIKE 的列名是固定字面量；用户输入只进已转义的绑定参数。 */
private fun <T> QueryWrapper<T>.containsLike(column: String, raw: String?) {
    val keyword = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
    apply("$column LIKE {0} ESCAPE '\\\\'", LikeEscape.containsPattern(keyword))
}

private fun <T> QueryWrapper<T>.prefixLike(column: String, raw: String?) {
    val keyword = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
    apply("$column LIKE {0} ESCAPE '\\\\'", LikeEscape.prefixPattern(keyword))
}
