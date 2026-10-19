package com.atguigu.hrspring.controller

import com.atguigu.hrspring.config.DocsConfig
import jakarta.servlet.http.MappingMatch
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.mock.web.MockHttpServletMapping
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tools.jackson.module.kotlin.jacksonObjectMapper

class DocsControllerTest {
    private fun docsContext() = WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration::class.java,
            WebMvcAutoConfiguration::class.java,
            SpringDocConfiguration::class.java,
            SpringDocConfigProperties::class.java,
            SwaggerUiConfigProperties::class.java,
            SwaggerUiOAuthProperties::class.java,
            SpringDocWebMvcConfiguration::class.java,
            SwaggerConfig::class.java,
        ))
        .withUserConfiguration(DocsController::class.java, DocsConfig::class.java)

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
                mvc.perform(get("/swagger")).andExpect(status().isFound)
                    .andExpect(redirectedUrl(path ?: "/swagger-ui.html"))
            }
        }
    }

    @Test
    fun `文档重定向保留context和servlet前缀`() = withMvc("/reference") { mvc ->
        for (context in listOf("", "/hr")) {
            for (servlet in listOf("", "/gateway")) {
                val prefix = context + servlet
                for (entry in listOf("/", "/docs", "/scalar", "/swagger")) {
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

    @Test
    fun `Knife4j页面及静态资源可访问并共用springdoc接口文档`() {
        docsContext()
            .run { context ->
                assertNull(context.startupFailure)
                val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                val html = mvc.perform(get("/doc.html")).andExpect(status().isOk)
                    .andReturn().response.contentAsString
                assertTrue(html.contains("Knife4j"))
                assertTrue(html.contains("<div id=app>"))
                assertTrue(!html.contains("swagger-ui-bundle"))
                val assets = Regex("(?:src|href)=(webjars/[^ >]+)").findAll(html)
                    .map { "/${it.groupValues[1]}" }.toSet()
                assertTrue(assets.isNotEmpty())
                assets.forEach { asset ->
                    val response = mvc.perform(get(asset)).andExpect(status().isOk).andReturn().response
                    assertTrue(response.contentAsByteArray.isNotEmpty(), asset)
                }
                val mapper = jacksonObjectMapper()
                val config = mvc.perform(get("/v3/api-docs/swagger-config"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val apiPath = mapper.readTree(config).path("url").asString()
                assertEquals("/v3/api-docs", apiPath)
                val api = mvc.perform(get(apiPath)).andExpect(status().isOk)
                    .andReturn().response.contentAsString
                assertTrue(mapper.readTree(api).path("openapi").asString().startsWith("3."))
                assertTrue(mapper.readTree(api).has("paths"))
            }
    }

    @Test
    fun `Knife4j固定配置入口兼容自定义OpenAPI路径和servlet前缀`() {
        for (servlet in listOf("", "/gateway")) {
            docsContext()
                .withPropertyValues(
                    "springdoc.api-docs.path=/reference",
                    "spring.mvc.servlet.path=${servlet.ifEmpty { "/" }}",
                )
                .run { context ->
                    assertNull(context.startupFailure)
                    val mvc = MockMvcBuilders.webAppContextSetup(context).build()
                    for (contextPath in listOf("", "/hr")) {
                        val prefix = contextPath + servlet
                        val request = get("$prefix/v3/api-docs/swagger-config").contextPath(contextPath)
                        if (servlet.isNotEmpty()) {
                            request.servletPath(servlet).pathInfo("/v3/api-docs/swagger-config").with {
                                it.setHttpServletMapping(
                                    MockHttpServletMapping(
                                        "v3/api-docs/swagger-config", "$servlet/*", "dispatcherServlet", MappingMatch.PATH,
                                    ),
                                )
                                it
                            }
                        }
                        mvc.perform(request).andExpect(status().isFound)
                            .andExpect(redirectedUrl("$prefix/reference/swagger-config"))
                    }
                    mvc.perform(get("/reference/swagger-config")).andExpect(status().isOk)
                    mvc.perform(get("/reference")).andExpect(status().isOk)
                }
        }
    }
}
