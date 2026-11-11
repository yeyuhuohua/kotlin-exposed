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
    /** 返回可重复读取缓存请求体的 Servlet 输入流。 */
    override fun getInputStream(): ServletInputStream {
        val input = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            /** 判断缓存请求体是否已经全部读取。 */
            override fun isFinished(): Boolean = input.available() == 0
            /** 判断缓存请求体是否始终可以非阻塞读取。 */
            override fun isReady(): Boolean = true
            /** 缓存请求体不支持异步监听，调用时直接报告不支持。 */
            override fun setReadListener(readListener: ReadListener?) {
                if (readListener == null) return
                try {
                    if (!isFinished) readListener.onDataAvailable()
                    readListener.onAllDataRead()
                } catch (error: Exception) {
                    readListener.onError(error)
                }
            }

            /** 读取当前输入中的数据。 */
            override fun read(): Int = input.read()
        }
    }

    /** 返回基于缓存请求体的字符读取器。 */
    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
}
