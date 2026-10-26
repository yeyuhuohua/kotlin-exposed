package com.atguigu.hrspring.controller

import com.atguigu.hrspring.common.api.ApiList
import com.atguigu.hrspring.common.api.PageParams
import com.atguigu.hrspring.config.GlobalExceptionHandler
import com.atguigu.hrspring.dto.EmployeeDto
import com.atguigu.hrspring.service.AuditQueryService
import com.atguigu.hrspring.service.EmployeeService
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.Test

class QueryValidationTest {
    private val employees = mock(EmployeeService::class.java)
    private val audit = mock(AuditQueryService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(EmployeeController(employees), AuditController(audit))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun `非法部门筛选返回400且不进入Service`() {
        mvc.perform(get("/api/employees").param("departmentId", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("validation_failed"))
        verifyNoInteractions(employees)
    }

    @Test
    fun `非法登录结果筛选返回400且不进入Service`() {
        mvc.perform(get("/api/audit/logins").param("success", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("validation_failed"))
        verifyNoInteractions(audit)
    }

    @Test
    fun `未分配部门筛选保持原有语义`() {
        `when`(employees.list(PageParams(), null, null, null, true))
            .thenReturn(ApiList(0, emptyList<EmployeeDto>()))
        mvc.perform(get("/api/employees").param("departmentId", "none"))
            .andExpect(status().isOk)
        verify(employees).list(PageParams(), null, null, null, true)
    }
}
