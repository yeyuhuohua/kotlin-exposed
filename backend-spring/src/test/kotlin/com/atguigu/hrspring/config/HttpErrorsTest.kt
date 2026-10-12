package com.atguigu.hrspring.config

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertTrue

class HttpErrorsTest {
    private val mvc = MockMvcBuilders.standaloneSetup(ProbeController())
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @Test
    fun `不支持的方法保留405及Allow响应头`() {
        val result = mvc.perform(post("/api/probe"))
            .andExpect(status().isMethodNotAllowed)
            .andReturn()
        assertTrue(result.response.getHeader("Allow")?.contains("GET") == true)
        assertTrue(result.response.getHeader("Allow")?.contains("PUT") == true)
        assertTrue(result.response.contentAsString.contains("method_not_allowed"))
    }

    @Test
    fun `错误媒体类型保留415及稳定错误码`() {
        val result = mvc.perform(put("/api/probe").contentType("text/plain").content("value"))
            .andExpect(status().isUnsupportedMediaType).andReturn()
        assertTrue(result.response.contentAsString.contains("unsupported_media_type"))
    }
}

@RestController
private class ProbeController {
    @GetMapping("/api/probe")
    fun get() = "ok"

    @PutMapping("/api/probe", consumes = ["application/json"])
    fun update(@RequestBody body: Map<String, Any>) = body
}
