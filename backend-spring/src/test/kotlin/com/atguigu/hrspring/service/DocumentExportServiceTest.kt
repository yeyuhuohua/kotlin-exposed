package com.atguigu.hrspring.service

import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.config.HrProperties
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentExportServiceTest {
    @TempDir
    lateinit var directory: Path

    private fun service(ttlSeconds: Long = 1800): DocumentExportService = DocumentExportService(
        HrProperties(
            export = HrProperties.ExportProperties(
                publicBaseUrl = "http://127.0.0.1:8081/",
                directory = directory.toString(),
                ttlSeconds = ttlSeconds,
            ),
        ),
    )

    @Test
    fun `表格写成 xlsx 并用链接中的凭据读回`() {
        val exporter = service()
        val created = exporter.exportExcel(
            filename = "入职名单",
            sheetsJson = """[{"name":"入职","headers":["姓名","人数"],"rows":[["张三",2]]}]""",
        )
        assertTrue(created.downloadUrl.startsWith("http://127.0.0.1:8081/api/exports/"))
        assertTrue(created.filename.endsWith(".xlsx"))
        val token = created.downloadUrl.substringAfter("token=")
        val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
        val file = checkNotNull(exporter.open(id, token))
        XSSFWorkbook(file.bytes.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals("入职", sheet.sheetName)
            assertEquals("姓名", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("张三", sheet.getRow(1).getCell(0).stringCellValue)
            val count = sheet.getRow(1).getCell(1)
            assertEquals(CellType.NUMERIC, count.cellType)
            assertEquals(2.0, count.numericCellValue)
        }
        assertNull(exporter.open(id, "wrong-token"))
    }

    @Test
    fun `正文写成 docx`() {
        val exporter = service()
        val created = exporter.exportWord(
            filename = null,
            title = "月报",
            blocksJson = """[
              {"type":"heading","level":2,"text":"摘要"},
              {"type":"paragraph","text":"本月入职 1 人。"},
              {"type":"table","headers":["姓名"],"rows":[["张三"]]}
            ]""",
        )
        val token = created.downloadUrl.substringAfter("token=")
        val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
        val file = checkNotNull(exporter.open(id, token))
        XWPFDocument(file.bytes.inputStream()).use { document ->
            assertEquals("月报", document.paragraphs[0].text)
            assertEquals("摘要", document.paragraphs[1].text)
            assertEquals("本月入职 1 人。", document.paragraphs[2].text)
            assertEquals("姓名", document.tables[0].getRow(0).getCell(0).text)
            assertEquals("张三", document.tables[0].getRow(1).getCell(0).text)
        }
    }

    @Test
    fun `正文写成 pdf`() {
        val exporter = service()
        try {
            val created = exporter.exportPdf(
                filename = "月报",
                title = "月报",
                blocksJson = """[
                  {"type":"paragraph","text":"本月入职 1 人。"},
                  {"type":"table","headers":["姓名"],"rows":[["张三"]]}
                ]""",
            )
            assertTrue(created.filename.endsWith(".pdf"))
            val token = created.downloadUrl.substringAfter("token=")
            val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
            val file = checkNotNull(exporter.open(id, token))
            Loader.loadPDF(file.bytes).use { document ->
                val text = PDFTextStripper().getText(document)
                assertTrue(text.contains("月报"))
                assertTrue(text.contains("张三"))
            }
        } catch (e: ApiException) {
            assertEquals("pdf font is unavailable", e.message)
        }
    }

    @Test
    fun `长表格行会拆到下一页`() {
        val marker = "尾部标记"
        val cell = "测".repeat(1800) + marker
        val exporter = service()
        val created = exporter.exportPdf(
            filename = null,
            title = "长表",
            blocksJson = """[{"type":"table","headers":["甲","乙","丙"],"rows":[["短","$cell","短"]]}]""",
        )
        assertFalse(created.truncated)
        val text = pdfText(exporter, created)
        assertTrue(text.contains("测"))
        assertTrue(text.contains(marker))
        Loader.loadPDF(pdfBytes(exporter, created)).use { document ->
            assertTrue(document.numberOfPages > 1)
        }
    }

    @Test
    fun `换行回车和制表符可以写入 pdf`() {
        val exporter = service()
        val created = exporter.exportPdf(
            filename = null,
            title = "控制字符",
            blocksJson = """[
              {"type":"paragraph","text":"第一行\r\n第二行\t第三"},
              {"type":"table","headers":["列"],"rows":[["上\n下"]]}
            ]""",
        )
        val text = pdfText(exporter, created)
        assertTrue(text.contains("第一行"))
        assertTrue(text.contains("第二行"))
        assertTrue(text.contains("第三"))
        assertTrue(text.contains("上"))
        assertTrue(text.contains("下"))
    }

    @Test
    fun `补充平面字符不会按代理项拆开`() {
        val emoji = String(Character.toChars(0x1F600))
        val exporter = service()
        val created = exporter.exportPdf(
            filename = null,
            title = "符号",
            blocksJson = """[{"type":"paragraph","text":"Face $emoji 结束"}]""",
        )
        val text = pdfText(exporter, created)
        assertTrue(text.contains("Face"))
        assertTrue(text.contains("结束"))
        if (!text.contains(emoji)) {
            assertTrue(created.truncated)
            assertTrue(text.contains("□") || text.contains("?"))
        }
    }

    @Test
    fun `缺字不会被静默删除`() {
        val rare = String(Character.toChars(0x20BB7))
        val name = rare + "田"
        val exporter = service()
        try {
            val created = exporter.exportPdf(
                filename = null,
                title = "姓名",
                blocksJson = """[
                  {"type":"paragraph","text":"$name"},
                  {"type":"table","headers":["姓名"],"rows":[["$name"]]}
                ]""",
            )
            val text = pdfText(exporter, created)
            assertTrue(text.contains("田"))
            if (!text.contains(rare)) {
                assertTrue(created.truncated)
                assertTrue(text.contains("□") || text.contains("?"))
            }
        } catch (e: ApiException) {
            assertEquals("pdf font is unavailable", e.message)
        } finally {
            exporter.close()
        }
    }

    @Test
    fun `缺字不会让后续字符失去可用字体`() {
        val latin = java.io.File("/System/Library/Fonts/Supplemental/Arial.ttf")
        val cjk = listOf(
            "/Library/Fonts/Arial Unicode.ttf",
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/System/Library/Fonts/Supplemental/Songti.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        ).any { java.io.File(it).isFile }
        if (!latin.isFile || !cjk) return
        val emoji = String(Character.toChars(0x1F600))
        val exporter = DocumentExportService(
            HrProperties(
                export = HrProperties.ExportProperties(
                    publicBaseUrl = "http://127.0.0.1:8081/",
                    directory = directory.toString(),
                    pdfFont = latin.path,
                ),
            ),
        )
        try {
            val emojiFirst = exporter.exportPdf(
                filename = null,
                title = "Report",
                blocksJson = """[{"type":"paragraph","text":"${emoji}中文"}]""",
            )
            val emojiFirstText = pdfText(exporter, emojiFirst)
            assertTrue(emojiFirstText.contains("中"))
            assertTrue(emojiFirstText.contains("文"))
            if (!emojiFirstText.contains(emoji)) {
                assertTrue(emojiFirst.truncated)
                assertTrue(emojiFirstText.contains("□") || emojiFirstText.contains("?"))
            }
            val chineseFirst = exporter.exportPdf(
                filename = null,
                title = "Report",
                blocksJson = """[{"type":"paragraph","text":"中文$emoji"}]""",
            )
            val chineseFirstText = pdfText(exporter, chineseFirst)
            assertTrue(chineseFirstText.contains("中"))
            assertTrue(chineseFirstText.contains("文"))
        } catch (e: ApiException) {
            assertEquals("pdf font is unavailable", e.message)
        } finally {
            exporter.close()
        }
    }

    @Test
    fun `损坏的字体集不会中断回退`() {
        val broken = directory.resolve("broken.ttc")
        Files.write(broken, byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        val exporter = DocumentExportService(
            HrProperties(
                export = HrProperties.ExportProperties(
                    publicBaseUrl = "http://127.0.0.1:8081/",
                    directory = directory.toString(),
                    pdfFont = broken.toString(),
                ),
            ),
        )
        try {
            val created = exporter.exportPdf(
                filename = null,
                title = "字体",
                blocksJson = """[{"type":"paragraph","text":"中文仍可显示"}]""",
            )
            assertTrue(created.filename.endsWith(".pdf"))
            assertTrue(pdfText(exporter, created).contains("仍可"))
        } catch (e: ApiException) {
            assertEquals("pdf font is unavailable", e.message)
        } finally {
            exporter.close()
        }
    }

    @Test
    fun `不能嵌入的字体面会继续尝试其他候选`() {
        val songti = java.io.File("/System/Library/Fonts/Supplemental/Songti.ttc")
        if (!songti.isFile) return
        val exporter = DocumentExportService(
            HrProperties(
                export = HrProperties.ExportProperties(
                    publicBaseUrl = "http://127.0.0.1:8081/",
                    directory = directory.toString(),
                    pdfFont = songti.path,
                ),
            ),
        )
        val created = exporter.exportPdf(
            filename = null,
            title = "字体",
            blocksJson = """[{"type":"paragraph","text":"中文仍可显示"}]""",
        )
        assertTrue(created.filename.endsWith(".pdf"))
        assertTrue(pdfText(exporter, created).contains("仍可"))
        exporter.close()
    }

    @Test
    fun `空表格拒绝生成`() {
        assertFailsWith<ApiException> {
            service().exportExcel("x", "[]")
        }
    }

    @Test
    fun `超大指数在展开前截断`() {
        val created = service().exportExcel(
            filename = null,
            sheetsJson = """[{"name":"S","headers":["n"],"rows":[[1e40000]]}]""",
        )
        assertTrue(created.truncated)
    }

    @Test
    fun `超大非整数仍保留 BigDecimal`() {
        val number = "1".repeat(310) + ".1"
        val exporter = service()
        val created = exporter.exportWord(
            filename = null,
            title = "大小数",
            blocksJson = """[{"type":"table","headers":["值"],"rows":[[$number]]}]""",
        )
        assertFalse(created.truncated)
        val token = created.downloadUrl.substringAfter("token=")
        val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
        val file = checkNotNull(exporter.open(id, token))
        XWPFDocument(file.bytes.inputStream()).use { document ->
            assertEquals(number, document.tables[0].getRow(1).getCell(0).text)
        }
    }

    @Test
    fun `关闭后旧实例目录可以被回收`() {
        val first = service()
        val firstDir = Files.list(directory).use { stream -> stream.filter { Files.isDirectory(it) }.findFirst().orElseThrow() }
        first.close()
        val second = service()
        second.sweepExports()
        assertFalse(Files.exists(firstDir))
        second.close()
    }

    @Test
    fun `超长文本截断后 truncated 为 true`() {
        val long = "a".repeat(2001)
        val created = service().exportExcel(
            filename = null,
            sheetsJson = """[{"name":"S","headers":["文本"],"rows":[["$long"]]}]""",
        )
        assertTrue(created.truncated)
    }

    @Test
    fun `多于表头的单元格被拒绝`() {
        val error = assertFailsWith<ApiException> {
            service().exportExcel(
                filename = null,
                sheetsJson = """[{"name":"S","headers":["只一列"],"rows":[["a","b"]]}]""",
            )
        }
        assertEquals("row has more cells than headers", error.message)
    }

    @Test
    fun `工作表名忽略大小写和首尾单引号`() {
        val used = mutableSetOf<String>()
        assertEquals("Sales", DocumentExportService.uniqueSheetName("'Sales'", used))
        assertEquals("sales 2", DocumentExportService.uniqueSheetName("sales", used))
        assertEquals("Sheet", DocumentExportService.sanitizeSheetName("''"))
    }

    @Test
    fun `希腊字母工作表名按 POI 规则视为同一个`() {
        val used = mutableSetOf<String>()
        assertEquals("Σ", DocumentExportService.uniqueSheetName("Σ", used))
        assertEquals("ς 2", DocumentExportService.uniqueSheetName("ς", used))
    }

    @Test
    fun `补充平面工作表名按不区分大小写比较`() {
        val capital = String(Character.toChars(0x10400))
        val small = String(Character.toChars(0x10428))
        val used = mutableSetOf<String>()
        assertEquals(capital, DocumentExportService.uniqueSheetName(capital, used))
        val renamed = DocumentExportService.uniqueSheetName(small, used)
        assertTrue(String.CASE_INSENSITIVE_ORDER.compare(capital, renamed) != 0)
    }

    @Test
    fun `Word 保留高精度小数原文`() {
        val number = "0.12345678901234567890123456789"
        val exporter = service()
        val created = exporter.exportWord(
            filename = null,
            title = "小数",
            blocksJson = """[{"type":"table","headers":["值"],"rows":[[$number]]}]""",
        )
        assertFalse(created.truncated)
        val token = created.downloadUrl.substringAfter("token=")
        val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
        val file = checkNotNull(exporter.open(id, token))
        XWPFDocument(file.bytes.inputStream()).use { document ->
            assertEquals(number, document.tables[0].getRow(1).getCell(0).text)
        }
    }

    @Test
    fun `Word 保留超出长整型的整数原文`() {
        val number = "9223372036854775808"
        val exporter = service()
        val created = exporter.exportWord(
            filename = null,
            title = "大整数",
            blocksJson = """[{"type":"table","headers":["值"],"rows":[[$number]]}]""",
        )
        assertFalse(created.truncated)
        val token = created.downloadUrl.substringAfter("token=")
        val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
        val file = checkNotNull(exporter.open(id, token))
        XWPFDocument(file.bytes.inputStream()).use { document ->
            assertEquals(number, document.tables[0].getRow(1).getCell(0).text)
        }
    }

    @Test
    fun `Word 总单元格超过上限时拒绝`() {
        val table = wordTable(rows = 2_000, columns = 30)
        val error = assertFailsWith<ApiException> {
            service().exportWord("x", "容量", "[$table,$table]")
        }
        assertTrue(error.message.orEmpty().contains("cells"))
    }

    @Test
    fun `只清理已退出实例里超过它自己 TTL 的文件`() {
        val plain = directory.resolve("notes.xlsx")
        Files.write(plain, byteArrayOf(1))
        Files.setLastModifiedTime(plain, FileTime.from(Instant.now().minusSeconds(4_000)))

        val alive = directory.resolve("a".repeat(32))
        Files.createDirectories(alive)
        val aliveLock = FileChannel.open(
            alive.resolve(".live"),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        aliveLock.write(java.nio.ByteBuffer.wrap("60".toByteArray()))
        aliveLock.tryLock()

        val dead = directory.resolve("b".repeat(32))
        Files.createDirectories(dead)
        Files.writeString(dead.resolve(".live"), "3600")
        Files.setLastModifiedTime(dead, FileTime.from(Instant.now().minusSeconds(120)))
        val stillValid = dead.resolve("c".repeat(32) + ".xlsx")
        val pastOwnerTtl = dead.resolve("d".repeat(32) + ".docx")
        Files.write(stillValid, byteArrayOf(1))
        Files.write(pastOwnerTtl, byteArrayOf(1))
        Files.setLastModifiedTime(stillValid, FileTime.from(Instant.now().minusSeconds(120)))
        Files.setLastModifiedTime(pastOwnerTtl, FileTime.from(Instant.now().minusSeconds(4_000)))

        val exporter = service(ttlSeconds = 60)
        exporter.sweepExports()

        assertTrue(Files.isDirectory(alive))
        assertTrue(Files.exists(plain))
        assertTrue(Files.exists(stillValid))
        assertFalse(Files.exists(pastOwnerTtl))

        Files.setLastModifiedTime(stillValid, FileTime.from(Instant.now().minusSeconds(4_000)))
        exporter.sweepExports()
        assertFalse(Files.exists(stillValid))
        aliveLock.close()
    }

    private fun pdfBytes(exporter: DocumentExportService, created: ExportResult): ByteArray {
        val token = created.downloadUrl.substringAfter("token=")
        val id = created.downloadUrl.substringAfter("/api/exports/").substringBefore("?")
        return checkNotNull(exporter.open(id, token)).bytes
    }

    private fun pdfText(exporter: DocumentExportService, created: ExportResult): String =
        Loader.loadPDF(pdfBytes(exporter, created)).use { document ->
            PDFTextStripper().getText(document)
        }

    private fun wordTable(rows: Int, columns: Int): String {
        val headers = (1..columns).joinToString(",") { "\"c$it\"" }
        val row = (1..columns).joinToString(",") { "1" }
        val body = (1..rows).joinToString(",") { "[$row]" }
        return """{"type":"table","headers":[$headers],"rows":[$body]}"""
    }
}
