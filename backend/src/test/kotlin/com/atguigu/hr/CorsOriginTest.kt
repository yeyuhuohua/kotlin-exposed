package com.atguigu.hr

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 未配置 cors.allowedHosts 时只放行本机来源。
 * Ktor 的 allowHost 只匹配默认端口，所以这里必须用端口无关的判断，
 * 否则 Vite 端口一变化（5173 被占用会顺延）开发环境就全线 403。
 */
class CorsOriginTest {
    @Test
    fun `放行本机任意端口与协议`() {
        assertTrue(isLocalOrigin("http://localhost:5173"))
        assertTrue(isLocalOrigin("http://localhost:5174"))
        assertTrue(isLocalOrigin("https://localhost"))
        assertTrue(isLocalOrigin("http://127.0.0.1:8080"))
        assertTrue(isLocalOrigin("http://[::1]:5173"))
    }

    @Test
    fun `拒绝其它来源`() {
        assertFalse(isLocalOrigin("http://evil.example"))
        assertFalse(isLocalOrigin("http://localhost.evil.example"))
        assertFalse(isLocalOrigin("http://192.168.1.10:5173"))
        assertFalse(isLocalOrigin("file://localhost"))
        assertFalse(isLocalOrigin(""))
        assertFalse(isLocalOrigin("localhost:5173"))
    }
}
