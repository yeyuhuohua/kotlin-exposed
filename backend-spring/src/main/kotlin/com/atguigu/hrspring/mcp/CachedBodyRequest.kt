package com.atguigu.hrspring.mcp

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/** 保留已读取的小型 JSON-RPC 请求体，供非工具列表请求继续交给 Spring AI。 */
class CachedBodyRequest(
    request: HttpServletRequest,
    private val body: ByteArray,
) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream {
        val input = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            override fun isFinished(): Boolean = input.available() == 0
            override fun isReady(): Boolean = true
            override fun setReadListener(readListener: ReadListener?) {
                if (readListener == null) return
                try {
                    if (!isFinished) readListener.onDataAvailable()
                    readListener.onAllDataRead()
                } catch (error: Exception) {
                    readListener.onError(error)
                }
            }

            override fun read(): Int = input.read()
        }
    }

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
}
