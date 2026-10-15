package com.atguigu.hrspring.mcp

import com.atguigu.hrspring.service.DocumentExportService
import com.atguigu.hrspring.service.ExportResult
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/** 把已经查到的表格或正文写成 Excel、Word 或 PDF。调用方把 downloadUrl 原样交给用户。 */
@Component
class ExportMcpTools(
    private val documentExportService: DocumentExportService,
) {
    @McpTool(
        name = "export_excel",
        description = "把给定表格写成 xlsx，返回浏览器可直接打开的临时下载链接。不要自己编造链接，也不要输出文件二进制。sheets 是 JSON 数组，每项含 name、headers、rows。rows 是二维数组，单元格可以是字符串或数字。示例：[{\"name\":\"入职\",\"headers\":[\"姓名\",\"部门\"],\"rows\":[[\"张三\",\"研发\"]]}]。最多 5 个表、每表 2000 行。",
    )
    fun exportExcel(
        @McpToolParam(description = "工作表 JSON 数组", required = true) sheets: String,
        @McpToolParam(description = "下载文件名，可不带扩展名", required = false) filename: String?,
    ): ExportResult = documentExportService.exportExcel(filename, sheets)

    @McpTool(
        name = "export_word",
        description = "把标题和正文写成 docx，返回临时下载链接。blocks 是 JSON 数组，type 只能是 heading、paragraph、table。heading 含 level(1-3) 和 text；paragraph 含 text；table 含 headers 和 rows。示例：[{\"type\":\"paragraph\",\"text\":\"本月入职 2 人。\"},{\"type\":\"table\",\"headers\":[\"姓名\"],\"rows\":[[\"张三\"]]}]。",
    )
    fun exportWord(
        @McpToolParam(description = "文档标题", required = true) title: String,
        @McpToolParam(description = "正文块 JSON 数组", required = true) blocks: String,
        @McpToolParam(description = "下载文件名，可不带扩展名", required = false) filename: String?,
    ): ExportResult = documentExportService.exportWord(filename, title, blocks)

    @McpTool(
        name = "export_pdf",
        description = "把已经查到的标题和正文写成 pdf，返回临时下载链接。不要自己编造链接。blocks 与 export_word 相同，type 只能是 heading、paragraph、table。heading 含 level(1-3) 和 text；paragraph 含 text；table 含 headers 和 rows。把 downloadUrl 原样告诉用户。truncated 为 true 时说明正文被截断，或有字符因字体缺字被替换。",
    )
    fun exportPdf(
        @McpToolParam(description = "文档标题", required = true) title: String,
        @McpToolParam(description = "正文块 JSON 数组", required = true) blocks: String,
        @McpToolParam(description = "下载文件名，可不带扩展名", required = false) filename: String?,
    ): ExportResult = documentExportService.exportPdf(filename, title, blocks)
}
