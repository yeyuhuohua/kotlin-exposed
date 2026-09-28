package com.atguigu.hr.common.database

import kotlin.test.Test
import kotlin.test.assertEquals

/** 用户输入进 LIKE 模式前必须转义，否则 %、_ 会被当成通配符。 */
class LikeEscapeTest {
    @Test
    fun `通配符与转义字符都按字面量处理`() {
        assertEquals("hr\\_admin", escapeLike("hr_admin"), "下划线必须转义，否则 hr_admin 会匹配 hrXadmin")
        assertEquals("100\\%", escapeLike("100%"))
        assertEquals("a\\\\b", escapeLike("a\\b"), "转义字符自身要先转义")
        assertEquals("plain", escapeLike("plain"))
        assertEquals("", escapeLike(""))
    }
}
