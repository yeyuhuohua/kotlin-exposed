package com.atguigu.hrspring.controller
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 文档入口兼容：保留 Ktor 后端的 /swagger、/doc.html 入口，统一落到 springdoc 的 Swagger UI。
 * /v3/api-docs 由 springdoc 自动提供。
 */
@RestController
class DocsController {

    private fun redirect(to: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).location(URI.create(to)).build()

    @GetMapping("/", "/docs", "/scalar")
    fun root(): ResponseEntity<Void> = redirect("/doc.html")

    @GetMapping("/doc.html")
    fun doc(): ResponseEntity<Void> = redirect("/swagger-ui/index.html")

    @GetMapping("/swagger")
    fun swagger(): ResponseEntity<Void> = redirect("/swagger-ui/index.html")
}
