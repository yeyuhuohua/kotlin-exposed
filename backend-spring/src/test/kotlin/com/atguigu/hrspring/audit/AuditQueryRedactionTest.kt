package com.atguigu.hrspring.audit

import kotlin.test.Test
import kotlin.test.assertEquals

class AuditQueryRedactionTest {
    @Test
    fun `编码后的 token 参数名也会脱敏`() {
        assertEquals("token=redacted", redactExportQuery("%74oken=secret"))
        assertEquals("a=1&token=redacted", redactExportQuery("a=1&Token=secret"))
        assertEquals("q=1", redactExportQuery("q=1"))
    }
}
