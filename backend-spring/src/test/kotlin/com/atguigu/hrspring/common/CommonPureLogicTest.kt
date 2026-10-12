package com.atguigu.hrspring.common.api

import com.atguigu.hrspring.common.validation.CodePointSize
import com.atguigu.hrspring.common.validation.HireDate
import com.atguigu.hrspring.dto.DepartmentUpdateRequest
import com.atguigu.hrspring.dto.JobCreateRequest
import com.atguigu.hrspring.dto.TEmpCreateRequest
import jakarta.validation.Validation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValidationTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `岗位编码不能包含路径分隔符或控制字符`() {
        for (id in listOf("DEV/OPS", "DEV\\OPS", "DEV\u0000", "DEV\nOPS", "DEV\u007f")) {
            assertTrue(validator.validate(JobCreateRequest(id, "Developer")).isNotEmpty(), id)
        }
        for (id in listOf("KT_DEV", "DEV-OPS", "DEV.OPS")) {
            assertTrue(validator.validate(JobCreateRequest(id, "Developer")).isEmpty(), id)
        }
    }

    @Test
    fun `长度按 Unicode 码点计数,增补平面字符不双算`() {
        val twenty = "𠮷" + "a".repeat(19)
        assertEquals(20, twenty.codePointCount(0, twenty.length))
        assertTrue(validator.validate(LengthSample(twenty)).isEmpty())
        assertTrue(validator.validate(LengthSample(twenty + "a")).isNotEmpty())
    }

    @Test
    fun `入职日期限制在 MySQL DATE 支持范围`() {
        assertTrue(validator.validate(DateSample("1000-01-01")).isEmpty())
        assertTrue(validator.validate(DateSample("9999-12-31")).isEmpty())
        assertTrue(validator.validate(DateSample("-0001-01-01")).isNotEmpty())
        assertTrue(validator.validate(DateSample("+10000-01-01")).isNotEmpty())
        assertTrue(validator.validate(DateSample("not-a-date")).isNotEmpty())
    }

    @Test
    fun `空更新和负数年龄会被注解拦住`() {
        val empty = validator.validate(DepartmentUpdateRequest())
        assertTrue(empty.any { it.message == "no fields to update" }, empty.joinToString { it.message })
        val negative = validator.validate(TEmpCreateRequest(name = null, age = -1, deptId = null, empno = 1))
        assertTrue(negative.any { it.message == "age must be non-negative" }, negative.joinToString { it.message })
    }

    @Test
    fun `岗位最低薪资不能高于最高薪资`() {
        val invalid = validator.validate(JobCreateRequest("DEV", "Developer", 100, 50))
        assertTrue(invalid.any { it.message == "minSalary must not exceed maxSalary" })
        for ((min, max) in listOf(50 to 100, 100 to 100, null to 100, 100 to null, null to null)) {
            assertTrue(validator.validate(JobCreateRequest("DEV", "Developer", min, max)).isEmpty())
        }
    }

    @Test
    fun `分页参数边界`() {
        assertEquals(50, PageParams().limit)
        assertFailsWith<ApiException> { PageParams(limit = 0) }
        assertFailsWith<ApiException> { PageParams(limit = 201) }
        assertFailsWith<ApiException> { PageParams(offset = -1) }
    }
}

private class LengthSample(
    @field:CodePointSize(max = 20)
    val value: String?,
)

private class DateSample(
    @field:HireDate
    val value: String?,
)

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
