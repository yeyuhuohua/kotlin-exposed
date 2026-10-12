package com.atguigu.hrspring.controller

import jakarta.servlet.http.MappingMatch
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.mock.web.MockHttpServletMapping
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.Test
import kotlin.test.assertNull

class DocsControllerTest {
    private fun withMvc(swaggerPath: String? = null, check: (MockMvc) -> Unit) {
        val runner = ApplicationContextRunner().withUserConfiguration(DocsController::class.java)
        val configured = if (swaggerPath == null) runner else {
            runner.withPropertyValues("springdoc.swagger-ui.path=$swaggerPath")
        }
        configured.run { context ->
            assertNull(context.startupFailure)
            check(MockMvcBuilders.standaloneSetup(context.getBean(DocsController::class.java)).build())
        }
    }

    @Test
    fun `文档入口使用配置的Swagger路径`() {
        for (path in listOf(null, "/reference")) {
            withMvc(path) { mvc ->
                for (entry in listOf("/doc.html", "/swagger")) {
                    mvc.perform(get(entry)).andExpect(status().isFound)
                        .andExpect(redirectedUrl(path ?: "/swagger-ui.html"))
                }
            }
        }
    }

    @Test
    fun `文档重定向保留context和servlet前缀`() = withMvc("/reference") { mvc ->
        for (context in listOf("", "/hr")) {
            for (servlet in listOf("", "/gateway")) {
                val prefix = context + servlet
                for (entry in listOf("/", "/docs", "/scalar", "/doc.html", "/swagger")) {
                    val target = if (entry in listOf("/", "/docs", "/scalar")) "/doc.html" else "/reference"
                    val request = get(prefix + entry).contextPath(context)
                    if (servlet.isNotEmpty()) {
                        request.servletPath(servlet).pathInfo(entry).with {
                            it.setHttpServletMapping(
                                MockHttpServletMapping(entry.drop(1), "$servlet/*", "dispatcherServlet", MappingMatch.PATH),
                            )
                            it
                        }
                    }
                    mvc.perform(request).andExpect(status().isFound)
                        .andExpect(redirectedUrl(prefix + target))
                }
            }
        }
    }
}
