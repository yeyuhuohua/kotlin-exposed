package com.atguigu.hrspring.common.api

/**
 * LIKE 转义:把用户输入中的 \ % _ 按字面量处理,再外加搜索通配符。
 */
object LikeEscape {
    const val ESCAPE_CHAR = '\\'

    fun escape(keyword: String): String = buildString(keyword.length) {
        for (c in keyword) {
            if (c == ESCAPE_CHAR || c == '%' || c == '_') append(ESCAPE_CHAR)
            append(c)
        }
    }

    /** 包含匹配模式:%kw%(已转义)。 */
    fun containsPattern(keyword: String): String = "%${escape(keyword)}%"

    /** 前缀匹配模式:kw%(已转义)。 */
    fun prefixPattern(keyword: String): String = "${escape(keyword)}%"
}
