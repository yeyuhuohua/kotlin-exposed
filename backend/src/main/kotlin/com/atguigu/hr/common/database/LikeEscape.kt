package com.atguigu.hr.common.database

/** LIKE 的转义字符：与 escapeLike 配套，传给 Exposed 的 like(pattern, escapeChar)。 */
const val LIKE_ESCAPE_CHAR = '\\'

/** 用户输入进 LIKE 模式前必须先转义：把 %、_ 和转义字符本身按字面量处理。 */
fun escapeLike(value: String): String = buildString(value.length) {
    for (char in value) {
        if (char == LIKE_ESCAPE_CHAR || char == '%' || char == '_') append(LIKE_ESCAPE_CHAR)
        append(char)
    }
}
