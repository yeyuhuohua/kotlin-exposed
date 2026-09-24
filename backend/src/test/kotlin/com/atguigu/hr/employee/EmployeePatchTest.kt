package com.atguigu.hr.employee

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PATCH 的语义是"出现才改、null 即清空"，解析错了会静默写错数据，
 * 因此未知字段、类型错误和清空非空列都必须报 400。
 */
class EmployeePatchTest {
    private fun parse(json: String): EmployeePatch = EmployeePatch.parse(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun `只把出现的字段标记为待更新`() {
        val patch = parse("""{"salary": 9000.5}""")
        assertEquals(setOf("salary"), patch.present)
        assertEquals(9000.5, patch.salary)
        assertNull(patch.departmentId)
    }

    @Test
    fun `显式 null 表示清空可空列`() {
        val patch = parse("""{"departmentId": null, "phoneNumber": null}""")
        assertEquals(setOf("departmentId", "phoneNumber"), patch.present)
        assertNull(patch.departmentId)
        assertNull(patch.phoneNumber)
    }

    @Test
    fun `可以同时改值和清空`() {
        val patch = parse("""{"salary": 1000, "departmentId": 90, "phoneNumber": null, "managerId": 100}""")
        assertEquals(setOf("salary", "departmentId", "phoneNumber", "managerId"), patch.present)
        assertEquals(90, patch.departmentId)
        assertEquals(100, patch.managerId)
    }

    @Test
    fun `未知字段直接拒绝而不是静默忽略`() {
        val error = assertFailsWith<IllegalArgumentException> { parse("""{"nickname": "x"}""") }
        assertTrue(error.message!!.contains("unknown fields"))
    }

    @Test
    fun `空请求体拒绝`() {
        assertFailsWith<IllegalArgumentException> { parse("{}") }
    }

    @Test
    fun `非空列不接受 null`() {
        val error = assertFailsWith<IllegalArgumentException> { parse("""{"jobId": null}""") }
        assertTrue(error.message!!.contains("jobId"))
        assertFailsWith<IllegalArgumentException> { parse("""{"jobId": "  "}""") }
    }

    @Test
    fun `类型不符时报错`() {
        assertFailsWith<IllegalArgumentException> { parse("""{"salary": "9000"}""") }
        assertFailsWith<IllegalArgumentException> { parse("""{"departmentId": 1.5}""") }
        assertFailsWith<IllegalArgumentException> { parse("""{"phoneNumber": 123}""") }
    }

    @Test
    fun `薪资不能为负`() {
        val error = assertFailsWith<IllegalArgumentException> { parse("""{"salary": -100}""") }
        assertTrue(error.message!!.contains("salary"))
        assertEquals(0.0, parse("""{"salary": 0}""").salary)
    }

    @Test
    fun `提成比例必须在 0 到 1 之间`() {
        assertFailsWith<IllegalArgumentException> { parse("""{"commissionPct": 2}""") }
        assertFailsWith<IllegalArgumentException> { parse("""{"commissionPct": -0.1}""") }
        assertEquals(0.35, parse("""{"commissionPct": 0.35}""").commissionPct)
    }

    @Test
    fun `新增与更新请求体的数值约束`() {
        assertEquals(
            "salary must be a non-negative number",
            EmployeeUpdateRequest(salary = -1.0).contentError(),
        )
        assertNull(EmployeeUpdateRequest(salary = 5000.0).contentError())
        assertEquals(
            "commissionPct must be between 0 and 1",
            createRequest(commissionPct = 1.5).contentError(),
        )
        assertEquals(
            "salary must be a non-negative number",
            createRequest(salary = -0.01).contentError(),
        )
        assertNull(createRequest(salary = 5000.0, commissionPct = 0.2).contentError())
        assertEquals(
            "lastName, email, hireDate, jobId are required",
            createRequest().copy(lastName = " ").contentError(),
        )
    }

    private fun createRequest(salary: Double? = null, commissionPct: Double? = null) = EmployeeCreateRequest(
        employeeId = 999,
        lastName = "Hire",
        email = "NHIRE999",
        hireDate = "2026-09-18",
        jobId = "IT_PROG",
        salary = salary,
        commissionPct = commissionPct,
    )
}
