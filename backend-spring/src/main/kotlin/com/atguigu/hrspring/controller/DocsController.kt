package com.atguigu.hrspring.controller
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.net.URI

/** 文档入口跳转；/doc.html 由 Knife4j UI 资源提供，/v3/api-docs 由 springdoc 生成。 */
@RestController
class DocsController(
    @Value("\${springdoc.swagger-ui.path:/swagger-ui.html}") private val swaggerUiPath: String,
) {

    /** 跳转到 Knife4j 文档页面。 */
    private fun redirect(request: HttpServletRequest, to: String): ResponseEntity<Void> {
        val path = ServletUriComponentsBuilder.fromServletMapping(request).path(to).build().toUri().rawPath
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).build()
    }

    /** 处理文档根路径访问。 */
    @GetMapping("/", "/docs", "/scalar")
    fun root(request: HttpServletRequest): ResponseEntity<Void> = redirect(request, "/doc.html")

    /** 提供 Swagger 文档页面。 */
    @GetMapping("/swagger")
    fun swagger(request: HttpServletRequest): ResponseEntity<Void> = redirect(request, swaggerUiPath)
}
