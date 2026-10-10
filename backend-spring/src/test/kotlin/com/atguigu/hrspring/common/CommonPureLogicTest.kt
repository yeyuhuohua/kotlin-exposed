package com.atguigu.hrspring.common.api

import com.atguigu.hrspring.common.api.Validation.codePointLength
import com.atguigu.hrspring.common.api.Validation.requireMaxLength
import com.atguigu.hrspring.common.api.Validation.requireMysqlDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidationTest {

    @Test
    fun `长度按 Unicode 码点计数,增补平面字符不双算`() {
        val twenty = "𠮷" + "a".repeat(19)
        assertEquals(20, twenty.codePointLength())
        requireMaxLength(twenty, "phone", 20)
        assertFailsWith<ApiException> {
            requireMaxLength(twenty + "a", "phone", 20)
        }
    }

    @Test
    fun `入职日期限制在 MySQL DATE 支持范围`() {
        requireMysqlDate("1000-01-01", "hireDate")
        requireMysqlDate("9999-12-31", "hireDate")
        assertFailsWith<ApiException> { requireMysqlDate("-0001-01-01", "hireDate") }
        assertFailsWith<ApiException> { requireMysqlDate("+10000-01-01", "hireDate") }
        assertFailsWith<ApiException> { requireMysqlDate("not-a-date", "hireDate") }
    }

    @Test
    fun `分页参数边界`() {
        assertEquals(50, PageParams().limit)
        assertFailsWith<ApiException> { PageParams(limit = 0) }
        assertFailsWith<ApiException> { PageParams(limit = 201) }
        assertFailsWith<ApiException> { PageParams(offset = -1) }
    }
}

class LikeEscapeTest {

    @Test
    fun `通配符与转义符被转义`() {
        assertEquals("%hr\\_admin%", LikeEscape.containsPattern("hr_admin"))
        assertEquals("%100\\%%", LikeEscape.containsPattern("100%"))
        assertEquals("%a\\\\b%", LikeEscape.containsPattern("a\\b"))
        assertEquals("hr\\_%", LikeEscape.prefixPattern("hr_"))
    }
}

class ClientIpTest {

    @Test
    fun `归一化 localhost 与 IPv6`() {
        assertEquals("127.0.0.1", ClientIp.normalize("localhost"))
        assertEquals("127.0.0.1", ClientIp.normalize("LOCALHOST"))
        assertEquals("0:0:0:0:0:0:0:1", ClientIp.normalize("::1"))
        assertEquals("2001:db8:0:0:0:0:0:10", ClientIp.normalize("2001:db8::10"))
        assertEquals("2001:db8:0:0:0:0:0:10", ClientIp.normalize("[2001:db8::10]"))
    }
}
