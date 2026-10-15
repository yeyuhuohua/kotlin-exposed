package com.atguigu.hrspring.controller

import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.service.DocumentExportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets

/** 模型生成的 Excel、Word 或 PDF。凭据在链接的 token 里，不走登录头。 */
@Tag(name = "exports")
@RestController
class ExportController(
    private val documentExportService: DocumentExportService,
) {
    @Operation(summary = "下载临时导出文件")
    @GetMapping("/api/exports/{id}")
    fun download(
        @PathVariable id: String,
        @RequestParam token: String,
    ): ResponseEntity<ByteArray> {
        val file = documentExportService.open(id, token)
            ?: throw ApiException.notFound("export not found")
        val disposition = ContentDisposition.attachment()
            .filename(file.filename, StandardCharsets.UTF_8)
            .build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.mediaType))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(file.bytes)
    }
}
