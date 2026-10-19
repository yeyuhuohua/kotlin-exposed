package com.atguigu.hrspring.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 保持 Knife4j 固定的配置入口兼容自定义的 springdoc 路径。 */
@Configuration(proxyBeanMethods = false)
class DocsConfig(
    @Value("\${springdoc.api-docs.path:/v3/api-docs}") private val apiDocsPath: String,
    @Value("\${spring.mvc.servlet.path:/}") private val servletPath: String,
) : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        if (apiDocsPath == "/v3/api-docs") return
        registry.addRedirectViewController(
            "/v3/api-docs/swagger-config",
            "${servletPath.trimEnd('/')}${apiDocsPath.trimEnd('/')}/swagger-config",
        )
    }
}
