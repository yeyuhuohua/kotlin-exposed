package com.atguigu.hr.demo

import com.atguigu.hr.demo.tdept.TDeptCreateRequest
import com.atguigu.hr.demo.tdept.TDeptUpdateRequest
import com.atguigu.hr.demo.temp.TEmpCreateRequest
import com.atguigu.hr.demo.temp.TEmpUpdateRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 示例表 t_dept（deptName/address ≤ 30）与 t_emp（name ≤ 20）的长度校验。 */
class DemoModelsLengthTest {
    @Test
    fun `t_dept 文本超过 30 个码点被拒绝`() {
        assertEquals(
            "deptName must be at most 30 characters",
            TDeptUpdateRequest(deptName = "a".repeat(31)).contentError(),
        )
        assertEquals(
            "address must be at most 30 characters",
            TDeptCreateRequest(address = "a".repeat(31)).contentError(),
        )
        assertNull(TDeptUpdateRequest(deptName = "恒山派", address = "大同").contentError())
        assertNull(TDeptCreateRequest(deptName = "𠮷" + "a".repeat(29)).contentError())
        assertEquals(
            "deptName must be at most 30 characters",
            TDeptCreateRequest(deptName = "𠮷" + "a".repeat(30)).contentError(),
        )
    }

    @Test
    fun `t_emp 姓名超过 20 个码点被拒绝`() {
        assertEquals("name must be at most 20 characters", TEmpUpdateRequest(name = "a".repeat(21)).contentError())
        assertEquals(
            "name must be at most 20 characters",
            TEmpCreateRequest(name = "a".repeat(21), empno = 2001).contentError(),
        )
        assertNull(TEmpUpdateRequest(name = "仪琳").contentError())
        assertNull(TEmpCreateRequest(name = "𠮷" + "a".repeat(19), empno = 2001).contentError())
    }

    @Test
    fun `t_emp 年龄为负被拒绝`() {
        assertEquals("age must be non-negative", TEmpUpdateRequest(age = -1).contentError())
        assertEquals("age must be non-negative", TEmpCreateRequest(age = -1, empno = 2001).contentError())
        assertNull(TEmpUpdateRequest(age = 0).contentError())
        assertNull(TEmpCreateRequest(age = 0, empno = 2001).contentError())
        assertNull(TEmpCreateRequest(empno = 2001).contentError())
    }
}
