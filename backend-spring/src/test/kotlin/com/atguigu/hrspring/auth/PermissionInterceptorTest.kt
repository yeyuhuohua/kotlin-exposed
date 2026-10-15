package com.atguigu.hrspring.auth

import com.atguigu.hrspring.common.api.ApiException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PermissionInterceptorTest {
    private val interceptor = PermissionInterceptor()

    @Test
    fun `带下载凭据的 HEAD 与 GET 都放行`() {
        assertTrue(interceptor.preHandle(download("GET"), MockHttpServletResponse(), Any()))
        assertTrue(interceptor.preHandle(download("HEAD"), MockHttpServletResponse(), Any()))
    }

    @Test
    fun `没有下载凭据的 HEAD 仍然拒绝`() {
        val request = MockHttpServletRequest("HEAD", "/api/exports/abc")
        val error = assertFailsWith<ApiException> {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
        assertEquals(401, error.status)
    }

    private fun download(method: String): MockHttpServletRequest =
        MockHttpServletRequest(method, "/api/exports/abc").apply {
            queryString = "token=secret"
            setParameter("token", "secret")
        }
}
