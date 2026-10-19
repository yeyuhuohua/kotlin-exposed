package com.atguigu.hrspring.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.web.server.autoconfigure.ServerProperties
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** 应用启动成功后输出当前配置下可访问的文档地址。 */
@Component
class StartupLogging {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onApplicationReady(event: ApplicationReadyEvent) {
        val context = event.applicationContext as? ServletWebServerApplicationContext ?: return
        val port = context.webServer?.port ?: return
        val environment = context.environment
        if (!environment.getProperty("springdoc.api-docs.enabled", Boolean::class.java, true)) return

        val server = context.getBean(ServerProperties::class.java)
        val address = server.address
        val host = address?.takeUnless { it.isAnyLocalAddress }?.hostAddress ?: "127.0.0.1"
        val browseHost = if (host.contains(':')) "[$host]" else host
        val scheme = if (server.ssl?.isEnabled == true) "https" else "http"
        val contextPath = environment.getProperty("server.servlet.context-path", "").trimEnd('/')
        val servletPath = environment.getProperty("spring.mvc.servlet.path", "/").trimEnd('/')
        val base = "$scheme://$browseHost:$port$contextPath$servletPath"

        if (environment.getProperty("springdoc.swagger-ui.enabled", Boolean::class.java, true)) {
            log.info("Knife4j: {}/doc.html", base)
            log.info("Swagger UI: {}{}", base, environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html"))
        }
        log.info("OpenAPI JSON: {}{}", base, environment.getProperty("springdoc.api-docs.path", "/v3/api-docs"))
    }
}
