package com.atguigu.hrspring.service

import com.atguigu.hrspring.common.api.ApiException
import com.atguigu.hrspring.config.HrProperties
import com.deepoove.poi.XWPFTemplate
import com.deepoove.poi.data.Documents
import com.deepoove.poi.data.Paragraphs
import com.deepoove.poi.data.Rows
import com.deepoove.poi.data.Tables
import com.deepoove.poi.data.Texts
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jsonMapper
import jakarta.annotation.PreDestroy
import org.apache.fesod.sheet.ExcelWriter
import org.apache.fesod.sheet.FesodSheet
import org.apache.fontbox.ttf.TTFParser
import org.apache.fontbox.ttf.TrueTypeCollection
import org.apache.fontbox.ttf.TrueTypeFont
import org.apache.pdfbox.io.RandomAccessReadBufferedFile
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * 把模型给出的表格或正文写成 xlsx、docx 或 pdf。文件只留在本机临时目录，
 * 下载凭据放在链接里，过期后删除。
 */
@Service
class DocumentExportService(
    properties: HrProperties,
) {
    private val pdfFont = properties.export.pdfFont.trim()
    private val fileStore = ExportFileStore(properties)
    /** 浮点按原文收成 BigDecimal，避免先变成 Double 再丢小数位。 */
    private val exportMapper: JsonMapper = jsonMapper {
        enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    }

    @PreDestroy
    fun close() = fileStore.close()

    /** sheetsJson 是已经查到的数据，本方法不再查库。 */
    fun exportExcel(filename: String?, sheetsJson: String): ExportResult {
        val parsed = parseSheets(sheetsJson)
        val safeName = safeFilename(filename, "export.xlsx", ".xlsx")
        val bytes = writeWorkbook(parsed.sheets)
        return store(safeName, XLSX, bytes, parsed.sheets.sumOf { it.rows.size }, parsed.truncated)
    }

    /** blocks 的 type 只能是 heading、paragraph、table。 */
    fun exportWord(filename: String?, title: String?, blocksJson: String): ExportResult {
        val documentTitle = title?.trim().orEmpty().ifBlank { "文档" }
        if (documentTitle.codePointCount(0, documentTitle.length) > 120) {
            throw ApiException.badRequest("title must be at most 120 characters")
        }
        val parsed = parseBlocks(blocksJson)
        val safeName = safeFilename(filename, "export.docx", ".docx")
        val bytes = writeDocument(documentTitle, parsed.blocks)
        val rows = parsed.blocks.sumOf { block -> if (block is WordTable) block.rows.size else 0 }
        return store(safeName, DOCX, bytes, rows, parsed.truncated)
    }

    /** 缺字被替换时 truncated 也为 true，和正文超长共用这一个标记。 */
    fun exportPdf(filename: String?, title: String?, blocksJson: String): ExportResult {
        val documentTitle = title?.trim().orEmpty().ifBlank { "文档" }
        if (documentTitle.codePointCount(0, documentTitle.length) > 120) {
            throw ApiException.badRequest("title must be at most 120 characters")
        }
        val parsed = parseBlocks(blocksJson)
        val safeName = safeFilename(filename, "export.pdf", ".pdf")
        val (bytes, omitted) = writePdf(documentTitle, parsed.blocks)
        val rows = parsed.blocks.sumOf { block -> if (block is WordTable) block.rows.size else 0 }
        return store(safeName, PDF, bytes, rows, parsed.truncated || omitted)
    }

    /** 过期条目和重启后脱离内存索引的文件都删掉。 */
    @Scheduled(fixedDelay = 60_000)
    fun sweepExports() = fileStore.sweepExports()

    /** 凭据只和哈希比较。过期、口令不对或文件已丢都返回 null。 */
    fun open(id: String, token: String): StoredFile? = fileStore.open(id, token)

    private fun store(
        filename: String,
        mediaType: String,
        bytes: ByteArray,
        rowCount: Int,
        truncated: Boolean,
    ): ExportResult = fileStore.store(filename, mediaType, bytes, rowCount, truncated)

    private fun parseSheets(raw: String): ParsedSheets {
        val state = ParseState()
        val root = readJson(raw, "sheets")
        if (!root.isArray) throw ApiException.badRequest("sheets must be a JSON array")
        if (root.size() !in 1..MAX_SHEETS) {
            throw ApiException.badRequest("sheets must contain 1 to $MAX_SHEETS items")
        }
        // JsonNode.map 只变换当前节点。子节点要走 values()。
        val sheets = root.values().map { node ->
            if (!node.isObject) throw ApiException.badRequest("each sheet must be an object")
            val headers = stringList(node.get("headers"), "headers", state)
            if (headers.isEmpty() || headers.size > MAX_COLUMNS) {
                throw ApiException.badRequest("each sheet needs 1 to $MAX_COLUMNS headers")
            }
            val rowsNode = node.get("rows") ?: throw ApiException.badRequest("rows is required")
            if (!rowsNode.isArray || rowsNode.size() > MAX_MODEL_ROWS) {
                throw ApiException.badRequest("rows must be an array of at most $MAX_MODEL_ROWS items")
            }
            state.charge(rowsNode.size(), headers.size)
            val rows = rowsNode.values().map { row -> alignedRow(row, headers.size, state) }
            SheetSpec(text(node.get("name"), "Sheet"), headers, rows)
        }
        return ParsedSheets(sheets, state.truncated)
    }

    private fun parseBlocks(raw: String): ParsedBlocks {
        val state = ParseState()
        val root = readJson(raw, "blocks")
        if (!root.isArray || root.size() !in 1..MAX_BLOCKS) {
            throw ApiException.badRequest("blocks must be a JSON array of 1 to $MAX_BLOCKS items")
        }
        val blocks = root.values().map { node ->
            if (!node.isObject) throw ApiException.badRequest("each block must be an object")
            when (text(node.get("type"), "")) {
                "heading" -> WordHeading(
                    level = node.get("level")?.asInt(1)?.coerceIn(1, 3) ?: 1,
                    text = requiredText(node.get("text"), "heading text", state),
                )
                "paragraph" -> WordParagraph(requiredText(node.get("text"), "paragraph text", state))
                "table" -> {
                    val headers = stringList(node.get("headers"), "headers", state)
                    if (headers.isEmpty() || headers.size > MAX_COLUMNS) {
                        throw ApiException.badRequest("a table needs 1 to $MAX_COLUMNS headers")
                    }
                    val rowsNode = node.get("rows") ?: throw ApiException.badRequest("table rows are required")
                    if (!rowsNode.isArray || rowsNode.size() > MAX_MODEL_ROWS) {
                        throw ApiException.badRequest("table rows must be an array of at most $MAX_MODEL_ROWS items")
                    }
                    state.charge(rowsNode.size(), headers.size)
                    WordTable(
                        headers,
                        rowsNode.values().map { row -> alignedRow(row, headers.size, state).map { wordCell(it) } },
                    )
                }
                else -> throw ApiException.badRequest("block type must be heading, paragraph or table")
            }
        }
        return ParsedBlocks(blocks, state.truncated)
    }

    private fun alignedRow(row: JsonNode, width: Int, state: ParseState): List<Any> {
        if (!row.isArray) throw ApiException.badRequest("each row must be an array")
        if (row.size() > width) throw ApiException.badRequest("row has more cells than headers")
        if (row.size() > MAX_COLUMNS) throw ApiException.badRequest("a row must have at most $MAX_COLUMNS cells")
        return List(width) { index -> cellValue(row.get(index), state) }
    }

    private fun readJson(raw: String, field: String): JsonNode {
        if (raw.length > MAX_JSON_CHARS) throw ApiException.badRequest("$field is too large")
        return try {
            exportMapper.readTree(raw)
        } catch (e: Exception) {
            throw ApiException.badRequest("$field must be JSON")
        }
    }

    private fun writeWorkbook(sheets: List<SheetSpec>): ByteArray {
        val usedNames = mutableSetOf<String>()
        val buffer = java.io.ByteArrayOutputStream()
        FesodSheet.write(buffer).build().use { writer: ExcelWriter ->
            sheets.forEachIndexed { index, spec ->
                val sheet = FesodSheet.writerSheet(index, uniqueSheetName(spec.name, usedNames))
                    .head(spec.headers.map { listOf(it) })
                    .build()
                writer.write(
                    spec.rows.map { row ->
                        row.map { cell -> if (cell is BigDecimal) cell.toPlainString() else cell }
                    },
                    sheet,
                )
            }
        }
        return buffer.toByteArray()
    }

    private fun writeDocument(title: String, blocks: List<WordBlock>): ByteArray {
        val document = Documents.of()
            .addParagraph(Paragraphs.of(Texts.of(title).bold().fontSize(18.0).create()).create())
        for (block in blocks) {
            when (block) {
                is WordHeading -> {
                    val size = if (block.level == 1) 16.0 else 14.0
                    document.addParagraph(Paragraphs.of(Texts.of(block.text).bold().fontSize(size).create()).create())
                }
                is WordParagraph -> document.addParagraph(Paragraphs.of(block.text).create())
                is WordTable -> {
                    val header = Rows.of(*block.headers.toTypedArray()).bgColor("D9D9D9").center().create()
                    val table = Tables.of(header)
                    for (row in block.rows) {
                        val cells = Array(block.headers.size) { index -> row.getOrElse(index) { "" } }
                        table.addRow(Rows.of(*cells).create())
                    }
                    document.addTable(table.create())
                }
            }
        }
        val buffer = java.io.ByteArrayOutputStream()
        XWPFTemplate.create(document.create()).use { template ->
            template.write(buffer)
        }
        return buffer.toByteArray()
    }

    private fun writePdf(title: String, blocks: List<WordBlock>): Pair<ByteArray, Boolean> {
        val document = PDDocument()
        val fonts = FontPool(document)
        try {
            fonts.prepare()
            val layout = PdfLayout(document, fonts.glyphs())
            layout.use {
                it.writeWrapped(title, 18f)
                it.gap(8f)
                for (block in blocks) {
                    when (block) {
                        is WordHeading -> {
                            it.gap(6f)
                            it.writeWrapped(block.text, if (block.level == 1) 16f else 14f)
                        }
                        is WordParagraph -> it.writeWrapped(block.text, 11f)
                        is WordTable -> it.writeTable(block.headers, block.rows, 10f)
                    }
                }
            }
            val buffer = java.io.ByteArrayOutputStream()
            document.save(buffer)
            return buffer.toByteArray() to layout.omitted
        } finally {
            document.close()
            fonts.close()
        }
    }

    /**
     * 主字体缺字时再打开后面的字体文件。字体集的构造和逐个字体面都在异常处理里，
     * 损坏的文件直接放弃。仅仅不包含当前字符的字体留在队列里，后面的字符还能再用。
     */
    private inner class FontPool(
        private val document: PDDocument,
    ) : AutoCloseable {
        private val pendingFiles = ArrayDeque(pdfFontFiles())
        private val revisitCollections = ArrayDeque<java.io.File>()
        private val missedCodePoints = mutableSetOf<Pair<String, Int>>()
        private val loaded = mutableListOf<PDFont>()
        private val faces = mutableListOf<TrueTypeFont>()
        private val collections = mutableListOf<TrueTypeCollection>()

        fun prepare() {
            if (loadNext(null) == null) throw ApiException.badRequest("pdf font is unavailable")
        }

        fun glyphs(): PdfGlyphFonts {
            val primary = loaded.first()
            return PdfGlyphFonts(primary) { glyph -> fontFor(glyph) }
        }

        override fun close() {
            faces.forEach { runCatching { it.close() } }
            collections.forEach { runCatching { it.close() } }
        }

        private fun fontFor(glyph: String): PDFont? {
            for (font in loaded) {
                if (measures(font, glyph)) return font
            }
            if (glyph.isEmpty()) return null
            val codePoint = glyph.codePointAt(0)
            while (true) {
                val font = loadNext(codePoint) ?: return null
                if (measures(font, glyph)) return font
            }
        }

        private fun loadNext(codePoint: Int?): PDFont? {
            val deferredFiles = ArrayDeque<java.io.File>()
            val deferredRevisit = ArrayDeque<java.io.File>()
            try {
                while (pendingFiles.isNotEmpty() || (codePoint != null && revisitCollections.isNotEmpty())) {
                    val revisiting = pendingFiles.isEmpty()
                    val file = if (revisiting) revisitCollections.removeFirst() else pendingFiles.removeFirst()
                    if (codePoint != null && missedCodePoints.contains(file.path to codePoint)) {
                        if (revisiting) deferredRevisit.addLast(file) else deferredFiles.addLast(file)
                        continue
                    }
                    when (val fate = openFile(file, codePoint)) {
                        is OpenFate.Found -> {
                            if (revisiting) {
                                deferredRevisit.addLast(file)
                            } else if (file.extension.equals("ttc", ignoreCase = true)) {
                                revisitCollections.addLast(file)
                            }
                            return fate.font
                        }
                        OpenFate.RetryLater -> {
                            if (codePoint != null) missedCodePoints += file.path to codePoint
                            if (revisiting) deferredRevisit.addLast(file) else deferredFiles.addLast(file)
                        }
                        OpenFate.Discard -> Unit
                    }
                }
                return null
            } finally {
                prepend(pendingFiles, deferredFiles)
                prepend(revisitCollections, deferredRevisit)
            }
        }

        private fun openFile(file: java.io.File, codePoint: Int?): OpenFate =
            if (file.extension.equals("ttc", ignoreCase = true)) {
                openCollection(file, codePoint)
            } else {
                openTrueType(file, codePoint)
            }

        private fun openTrueType(file: java.io.File, codePoint: Int?): OpenFate {
            val face = try {
                TTFParser().parse(RandomAccessReadBufferedFile(file))
            } catch (e: Exception) {
                return OpenFate.Discard
            }
            var keep = false
            try {
                if (!canEmbedSubset(face)) return OpenFate.Discard
                if (codePoint != null && !covers(face, codePoint)) return OpenFate.RetryLater
                val font = try {
                    PDType0Font.load(document, face, true)
                } catch (e: Exception) {
                    return OpenFate.Discard
                }
                faces += face
                keep = true
                loaded += font
                if (codePoint != null && !measures(font, String(Character.toChars(codePoint)))) {
                    return OpenFate.Discard
                }
                return OpenFate.Found(font)
            } finally {
                if (!keep) runCatching { face.close() }
            }
        }

        private fun openCollection(file: java.io.File, codePoint: Int?): OpenFate {
            val collection = try {
                TrueTypeCollection(file)
            } catch (e: Exception) {
                return OpenFate.Discard
            }
            var chosen: PDFont? = null
            var sawEmbeddable = false
            val retainedBefore = faces.size
            try {
                collection.processAllFonts { face ->
                    if (chosen != null) return@processAllFonts
                    if (!canEmbedSubset(face)) return@processAllFonts
                    sawEmbeddable = true
                    if (codePoint != null && !covers(face, codePoint)) return@processAllFonts
                    val font = try {
                        PDType0Font.load(document, face, true)
                    } catch (e: Exception) {
                        return@processAllFonts
                    }
                    faces += face
                    loaded += font
                    if (codePoint != null && !measures(font, String(Character.toChars(codePoint)))) {
                        return@processAllFonts
                    }
                    chosen = font
                    throw FontReady()
                }
            } catch (ready: FontReady) {
                // 已经选中一个可嵌入的字体面，不再解析同文件里后面的面。
            } catch (e: Exception) {
                if (chosen == null) sawEmbeddable = false
            }
            if (faces.size == retainedBefore) {
                runCatching { collection.close() }
            } else {
                collections += collection
            }
            val selected = chosen
            return when {
                selected != null -> OpenFate.Found(selected)
                sawEmbeddable -> OpenFate.RetryLater
                else -> OpenFate.Discard
            }
        }
    }

    /**
     * 打开一个字体文件的结果。
     * RetryLater 表示文件能用，只是没有当前字符，必须留在队列里给后面的字。
     * Discard 表示文件损坏或禁止嵌入，不再重试。
     */
    private sealed interface OpenFate {
        data class Found(val font: PDFont) : OpenFate
        data object RetryLater : OpenFate
        data object Discard : OpenFate
    }

    private fun prepend(target: ArrayDeque<java.io.File>, items: ArrayDeque<java.io.File>) {
        if (items.isEmpty()) return
        val rest = ArrayDeque(target)
        target.clear()
        target.addAll(items)
        target.addAll(rest)
    }

    /** 打断 TrueTypeCollection.processAllFonts，避免把同文件后面的字体面全部解析进内存。 */
    private class FontReady : RuntimeException()

    /** 配置的字体放在最前，其余是本机常见的中文字体。不存在的路径直接略过。 */
    private fun pdfFontFiles(): List<java.io.File> {
        val configured = pdfFont.takeIf { it.isNotEmpty() }?.let { java.io.File(it) }?.takeIf { it.isFile }
        val discovered = PDF_FONT_CANDIDATES.map { java.io.File(it) }.filter { it.isFile }
        return listOfNotNull(configured) + discovered.filter { file ->
            configured == null || file.absolutePath != configured.absolutePath
        }
    }

    /** Word 单元格保留十进制原文，避免大整数先变成 Double 再丢精度。 */
    private fun wordCell(value: Any): String = when (value) {
        is BigDecimal -> value.toPlainString()
        else -> value.toString()
    }

    private fun stringList(node: JsonNode?, field: String, state: ParseState): List<String> {
        if (node == null || node.isNull) return emptyList()
        if (!node.isArray) throw ApiException.badRequest("$field must be an array")
        return node.values().map { cellValue(it, state).toString() }
    }

    private fun requiredText(node: JsonNode?, field: String, state: ParseState): String {
        val value = cellValue(node, state).toString()
        if (value.isBlank()) throw ApiException.badRequest("$field is required")
        return value
    }

    private fun text(node: JsonNode?, fallback: String): String {
        val value = when {
            node == null || node.isNull || node.isMissingNode -> ""
            node.isTextual -> node.asText()
            else -> node.asText("")
        }
        return value.ifBlank { fallback }.take(200)
    }

    /** 数字保持为数字。超长文本或展开后超长的数字截断，并记入 truncated。 */
    private fun cellValue(node: JsonNode?, state: ParseState): Any {
        if (node == null || node.isNull || node.isMissingNode) return ""
        if (node.isNumber) return numberValue(node.decimalValue(), state)
        val text = if (node.isTextual) node.asText() else node.asText("")
        if (text.length <= MAX_CELL_CHARS) return text
        state.truncated = true
        return text.take(MAX_CELL_CHARS)
    }

    /**
     * 能精确放进 Long 或 Double 的留给 Excel 数字单元格。
     * 超大整数和非有限小数保持 BigDecimal，避免变成 Infinity。
     */
    private fun numberValue(decimal: BigDecimal, state: ParseState): Any {
        val plain = decimal.stripTrailingZeros()
        if (numberExceedsCell(plain)) {
            state.truncated = true
            val compact = plain.toString()
            return if (compact.length <= MAX_CELL_CHARS) compact else compact.take(MAX_CELL_CHARS)
        }
        if (plain.scale() <= 0) {
            try {
                return plain.longValueExact()
            } catch (e: ArithmeticException) {
                return plain
            }
        }
        val asDouble = plain.toDouble()
        if (!asDouble.isFinite()) return plain
        return if (BigDecimal.valueOf(asDouble).compareTo(plain) == 0) asDouble else plain
    }

    /** 先看精度和指数，避免 1e40000 展开成四万个字符。 */
    private fun numberExceedsCell(value: BigDecimal): Boolean {
        if (value.precision() > MAX_CELL_CHARS) return true
        val scale = value.scale()
        if (scale > MAX_CELL_CHARS || scale < -MAX_CELL_CHARS) return true
        return plainLength(value) > MAX_CELL_CHARS
    }

    private fun plainLength(value: BigDecimal): Int {
        val precision = value.precision().coerceAtLeast(1)
        val scale = value.scale()
        val digits = when {
            scale <= 0 -> precision + (-scale)
            precision > scale -> precision + 1
            else -> scale + 2
        }
        return digits + if (value.signum() < 0) 1 else 0
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val PDF = "application/pdf"
        private const val MAX_SHEETS = 5
        private const val MAX_SHEET_NAME = 31
        private const val MAX_COLUMNS = 30
        private const val MAX_MODEL_ROWS = 2000
        private const val MAX_BLOCKS = 200
        private const val MAX_CELL_CHARS = 2000
        internal const val MAX_TOTAL_CELLS = 100_000L
        private const val MAX_JSON_CHARS = 2_000_000
        private val filenameCleaner = Regex("""[^\p{L}\p{N}._ -]+""")
        /** 未配置 hr.export.pdf-font 时的查找顺序。前面的文件缺字时仍会试后面的。 */
        private val PDF_FONT_CANDIDATES = listOf(
            "/Library/Fonts/Arial Unicode.ttf",
            "/System/Library/Fonts/Supplemental/Songti.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc",
        )
        internal const val PDF_MARGIN = 48f

        fun safeFilename(raw: String?, fallback: String, extension: String): String {
            val cleaned = raw?.replace(filenameCleaner, "")?.trim()?.trim('.').orEmpty()
            val base = cleaned.substringBeforeLast('.').take(80).ifBlank {
                fallback.substringBeforeLast('.')
            }
            return base + extension
        }

        fun uniqueSheetName(raw: String, used: MutableSet<String>): String {
            val cleaned = sanitizeSheetName(raw)
            var candidate = cleaned
            var index = 2
            while (used.any { String.CASE_INSENSITIVE_ORDER.compare(it, candidate) == 0 }) {
                val suffix = " $index"
                val room = (MAX_SHEET_NAME - suffix.length).coerceAtLeast(1)
                candidate = sanitizeSheetName(cleaned.take(room)) + suffix
                index++
            }
            used.add(candidate)
            return candidate
        }

        /** Excel 表名不区分大小写，且不能以单引号开头或结尾。 */
        fun sanitizeSheetName(raw: String): String {
            var value = raw.replace(Regex("""[:\\/?*\[\]]"""), " ").trim()
            while (value.startsWith("'") || value.endsWith("'")) {
                value = value.removePrefix("'").removeSuffix("'").trim()
            }
            value = value.take(MAX_SHEET_NAME)
            while (value.startsWith("'") || value.endsWith("'")) {
                value = value.removePrefix("'").removeSuffix("'").trim()
            }
            return value.ifBlank { "Sheet" }
        }
    }
}

data class ExportResult(
    val filename: String,
    /** 带 token 的临时链接，调用方原样交给用户。 */
    val downloadUrl: String,
    val expiresAt: String,
    val rowCount: Int,
    /** 单元格被截断，或 PDF 有字符因缺字换成了 □ / ?。 */
    val truncated: Boolean,
)

data class StoredFile(
    val filename: String,
    val mediaType: String,
    val bytes: ByteArray,
)

private data class ParsedSheets(val sheets: List<SheetSpec>, val truncated: Boolean)

private data class ParsedBlocks(val blocks: List<WordBlock>, val truncated: Boolean)

private class ParseState {
    var truncated: Boolean = false
    private var cells: Long = 0

    fun charge(rows: Int, columns: Int) {
        val added = rows.toLong() * columns
        if (added < 0 || cells > DocumentExportService.MAX_TOTAL_CELLS - added) {
            throw ApiException.badRequest("export exceeds ${DocumentExportService.MAX_TOTAL_CELLS} cells")
        }
        cells += added
    }
}

private data class SheetSpec(
    val name: String,
    val headers: List<String>,
    val rows: List<List<Any>>,
)

private sealed interface WordBlock
private data class WordHeading(val level: Int, val text: String) : WordBlock
private data class WordParagraph(val text: String) : WordBlock
private data class WordTable(val headers: List<String>, val rows: List<List<String>>) : WordBlock
