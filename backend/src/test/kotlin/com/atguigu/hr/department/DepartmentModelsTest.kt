package com.atguigu.hr.department

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 部门名称长度按列上限 30 校验，且按 Unicode 码点计数。 */
class DepartmentModelsTest {
    @Test
    fun `部门名称超过 30 个码点被拒绝`() {
        assertEquals(
            "departmentName must be at most 30 characters",
            DepartmentUpdateRequest(departmentName = "a".repeat(31)).contentError(),
        )
        assertEquals(
            "departmentName must be at most 30 characters",
            DepartmentCreateRequest(departmentId = 280, departmentName = "a".repeat(31)).contentError(),
        )
        assertNull(DepartmentUpdateRequest(departmentName = "a".repeat(30)).contentError())
        assertNull(DepartmentCreateRequest(departmentId = 280, departmentName = "a".repeat(30)).contentError())
    }

    @Test
    fun `空白与增补平面字符`() {
        assertEquals("departmentName must not be blank", DepartmentUpdateRequest(departmentName = " ").contentError())
        // 𠮷 是一个码点两个 UTF-16 单元：加 29 个普通字符共 30 个码点，应放行
        assertNull(DepartmentUpdateRequest(departmentName = "𠮷" + "a".repeat(29)).contentError())
        assertEquals(
            "departmentName must be at most 30 characters",
            DepartmentUpdateRequest(departmentName = "𠮷" + "a".repeat(30)).contentError(),
        )
    }
}
