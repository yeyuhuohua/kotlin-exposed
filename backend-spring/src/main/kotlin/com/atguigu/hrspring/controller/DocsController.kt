package com.atguigu.hrspring.controller
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

/**
 * 文档入口兼容：保留 Ktor 后端的 /swagger、/doc.html 入口，统一落到 springdoc 的 Swagger UI。
 * /v3/api-docs 由 springdoc 自动提供。
 */
@RestController
class DocsController(
    @Value("\${springdoc.swagger-ui.path:/swagger-ui.html}") private val swaggerUiPath: String,
) {

    private fun redirect(request: HttpServletRequest, to: String): ResponseEntity<Void> {
        val path = ServletUriComponentsBuilder.fromServletMapping(request).path(to).build().toUri().rawPath
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).build()
    }

    @GetMapping("/", "/docs", "/scalar")
    fun root(request: HttpServletRequest): ResponseEntity<Void> = redirect(request, "/doc.html")

    @GetMapping("/doc.html")
    fun doc(request: HttpServletRequest): ResponseEntity<Void> = redirect(request, swaggerUiPath)

    @GetMapping("/swagger")
    fun swagger(request: HttpServletRequest): ResponseEntity<Void> = redirect(request, swaggerUiPath)
}
