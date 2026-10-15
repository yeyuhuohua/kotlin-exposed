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
import org.apache.fontbox.ttf.OS2WindowsMetricsTable
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
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes

/**
 * 把模型给出的表格或正文写成 xlsx、docx 或 pdf。文件只留在本机临时目录，
 * 下载凭据放在链接里，过期后删除。
 */
@Service
class DocumentExportService(
    properties: HrProperties,
) {
    private val publicBaseUrl = properties.export.publicBaseUrl.trim().trimEnd('/')
    private val ttlSeconds = properties.export.ttlSeconds.coerceIn(60, 86_400)
    private val pdfFont = properties.export.pdfFont.trim()
    private val files = ConcurrentHashMap<String, StoredExport>()
    private val random = SecureRandom()
    private val instanceId = newId()
    private val base: Path = resolveBase(properties.export.directory)
    private val root: Path = base.resolve(instanceId)
    /** 浮点按原文收成 BigDecimal，避免先变成 Double 再丢小数位。 */
    private val exportMapper: JsonMapper = jsonMapper {
        enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    }
    private var liveChannel: FileChannel? = null
    private var liveLock: FileLock? = null
    private val released = AtomicBoolean(false)
    private val shutdownHook = Thread { releaseInstanceLock() }

    init {
        var hookAdded = false
        try {
            Files.createDirectories(root)
            val livePath = root.resolve(LIVE_FILE)
            val channel = FileChannel.open(
                livePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            liveChannel = channel
            val lock = channel.tryLock()
                ?: throw IllegalStateException("export instance lock is unavailable")
            liveLock = lock
            val payload = ByteBuffer.wrap(ttlSeconds.toString().toByteArray(StandardCharsets.UTF_8))
            channel.truncate(0)
            channel.position(0)
            channel.write(payload)
            channel.force(true)
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            hookAdded = true
            sweepOtherInstances()
            sweepOrphans()
        } catch (e: Exception) {
            releaseInstanceLock()
            if (hookAdded) {
                runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
            }
            throw e
        }
    }

    /** 容器关闭或初始化失败时放开锁，同一 JVM 里的新实例才能回收这个目录。 */
    @PreDestroy
    fun close() {
        releaseInstanceLock()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }

    private fun releaseInstanceLock() {
        if (!released.compareAndSet(false, true)) return
        try {
            liveLock?.takeIf { it.isValid }?.release()
        } catch (e: Exception) {
            // 锁已经放开
        }
        try {
            liveChannel?.takeIf { it.isOpen }?.close()
        } catch (e: Exception) {
            // 通道已经关闭
        }
    }

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
    fun sweepExports() {
        purgeExpired()
        sweepOrphans()
        sweepOtherInstances()
    }

    /** 凭据只和哈希比较。过期、口令不对或文件已丢都返回 null。 */
    fun open(id: String, token: String): StoredFile? {
        purgeExpired()
        val stored = files[id] ?: return null
        if (Instant.now().isAfter(stored.expiresAt) || !tokenMatches(token, stored.tokenHash)) {
            if (Instant.now().isAfter(stored.expiresAt)) remove(id, stored)
            return null
        }
        if (!Files.isRegularFile(stored.path)) {
            files.remove(id, stored)
            return null
        }
        return StoredFile(stored.filename, stored.mediaType, stored.path.readBytes())
    }

    private fun store(
        filename: String,
        mediaType: String,
        bytes: ByteArray,
        rowCount: Int,
        truncated: Boolean,
    ): ExportResult {
        purgeExpired()
        val id = newId()
        val token = newToken()
        val path = root.resolve(id + extensionFor(mediaType))
        Files.write(path, bytes)
        val expiresAt = Instant.now().plusSeconds(ttlSeconds)
        files[id] = StoredExport(sha256(token), path, filename, mediaType, expiresAt)
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8)
        return ExportResult(
            filename = filename,
            downloadUrl = "$publicBaseUrl/api/exports/$id?token=$encodedToken",
            expiresAt = DateTimeFormatter.ISO_INSTANT.format(expiresAt),
            rowCount = rowCount,
            truncated = truncated,
        )
    }

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

    /** 与 PDFBox 的子集嵌入规则一致：受限、仅位图或禁止子集的字体跳过。 */
    private fun canEmbedSubset(face: TrueTypeFont): Boolean {
        val os2 = try {
            face.getOS2Windows()
        } catch (e: Exception) {
            return false
        } ?: return true
        val fsType = os2.getFsType().toInt()
        val masked = fsType and 0x000F
        if (masked == OS2WindowsMetricsTable.FSTYPE_RESTRICTED.toInt()) return false
        if (fsType and OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY.toInt() ==
            OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY.toInt()
        ) {
            return false
        }
        return fsType and OS2WindowsMetricsTable.FSTYPE_NO_SUBSETTING.toInt() !=
            OS2WindowsMetricsTable.FSTYPE_NO_SUBSETTING.toInt()
    }

    /** 配置的字体放在最前，其余是本机常见的中文字体。不存在的路径直接略过。 */
    private fun pdfFontFiles(): List<java.io.File> {
        val configured = pdfFont.takeIf { it.isNotEmpty() }?.let { java.io.File(it) }?.takeIf { it.isFile }
        val discovered = PDF_FONT_CANDIDATES.map { java.io.File(it) }.filter { it.isFile }
        return listOfNotNull(configured) + discovered.filter { file ->
            configured == null || file.absolutePath != configured.absolutePath
        }
    }

    private fun extensionFor(mediaType: String): String = when (mediaType) {
        XLSX -> ".xlsx"
        DOCX -> ".docx"
        PDF -> ".pdf"
        else -> error("unknown export type")
    }

    private fun purgeExpired() {
        val now = Instant.now()
        for ((id, stored) in files) {
            if (now.isAfter(stored.expiresAt)) remove(id, stored)
        }
    }

    /** 只清理本实例目录里、由本服务命名的文件。 */
    private fun sweepOrphans() {
        val live = files.values.mapTo(mutableSetOf()) { it.path.fileName.toString() }
        sweepOwnedFiles(root, Instant.now().minusSeconds(ORPHAN_GRACE_SECONDS), live)
    }

    /**
     * 其他实例目录只在确认进程已退出后清理，并使用该实例自己写下的 TTL。
     * 仍持有锁的目录，包括还没写入文件的空目录，一律不动。
     */
    private fun sweepOtherInstances() {
        withSweepLock {
            val listed = try {
                Files.list(base)
            } catch (e: NoSuchFileException) {
                return@withSweepLock
            } catch (e: Exception) {
                return@withSweepLock
            }
            listed.use { stream ->
                stream.forEach { dir ->
                    try {
                        if (!Files.isDirectory(dir)) return@forEach
                        val name = dir.fileName.toString()
                        if (name == instanceId || !INSTANCE_DIR.matches(name)) return@forEach
                        if (instanceIsAlive(dir)) return@forEach
                        sweepOwnedFiles(dir, Instant.now().minusSeconds(recordedTtl(dir)), emptySet())
                        if (onlyMarkerLeft(dir)) {
                            dir.resolve(LIVE_FILE).deleteIfExists()
                            dir.deleteIfExists()
                        }
                    } catch (e: NoSuchFileException) {
                        // 目录刚被其他实例删掉
                    } catch (e: DirectoryNotEmptyException) {
                        // 对方刚写入新文件
                    }
                }
            }
        }
    }

    private fun withSweepLock(block: () -> Unit) {
        synchronized(sweepMonitor) {
            val lockPath = base.resolve(CLEANUP_LOCK)
            try {
                FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                ).use { channel ->
                    val lock = channel.lock()
                    try {
                        block()
                    } finally {
                        try {
                            lock.release()
                        } catch (e: Exception) {
                            // 锁已经放开
                        }
                    }
                }
            } catch (e: NoSuchFileException) {
                // 导出根目录已经不在
            }
        }
    }

    private fun instanceIsAlive(dir: Path): Boolean {
        val live = dir.resolve(LIVE_FILE)
        if (!Files.isRegularFile(live)) {
            return try {
                Files.getLastModifiedTime(dir).toInstant()
                    .isAfter(Instant.now().minusSeconds(ORPHAN_GRACE_SECONDS))
            } catch (e: Exception) {
                true
            }
        }
        return try {
            FileChannel.open(live, StandardOpenOption.WRITE).use { channel ->
                val lock = channel.tryLock()
                if (lock == null) {
                    true
                } else {
                    lock.release()
                    false
                }
            }
        } catch (e: OverlappingFileLockException) {
            true
        } catch (e: Exception) {
            true
        }
    }

    private fun recordedTtl(dir: Path): Long =
        try {
            Files.readString(dir.resolve(LIVE_FILE)).trim().toLong().coerceIn(60, 86_400)
        } catch (e: Exception) {
            86_400
        }

    private fun onlyMarkerLeft(dir: Path): Boolean =
        try {
            Files.list(dir).use { stream ->
                stream.allMatch { it.fileName.toString() == LIVE_FILE }
            }
        } catch (e: NoSuchFileException) {
            false
        }

    private fun sweepOwnedFiles(dir: Path, cutoff: Instant, live: Set<String>) {
        val listed = try {
            Files.list(dir)
        } catch (e: NoSuchFileException) {
            return
        } catch (e: Exception) {
            return
        }
        listed.use { stream ->
            stream.forEach { path ->
                try {
                    if (!Files.isRegularFile(path)) return@forEach
                    val name = path.fileName.toString()
                    if (!OWNED_FILE.matches(name) || name in live) return@forEach
                    val modified = Files.getLastModifiedTime(path).toInstant()
                    if (modified.isBefore(cutoff)) path.deleteIfExists()
                } catch (e: NoSuchFileException) {
                    // 文件刚被其他实例删掉
                }
            }
        }
    }

    private fun remove(id: String, stored: StoredExport) {
        if (files.remove(id, stored)) stored.path.deleteIfExists()
    }

    private fun tokenMatches(token: String, expected: ByteArray): Boolean =
        MessageDigest.isEqual(sha256(token), expected)

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))

    private fun newId(): String = buildString(32) {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        bytes.forEach { append("%02x".format(it)) }
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun resolveBase(configured: String): Path {
        val path = if (configured.isBlank()) {
            Path.of(System.getProperty("java.io.tmpdir"), "hr-spring-exports")
        } else {
            Path.of(configured)
        }
        return try {
            Files.createDirectories(path)
        } catch (e: Exception) {
            throw IllegalStateException("export directory is unavailable")
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
        private const val ORPHAN_GRACE_SECONDS = 60L
        private val filenameCleaner = Regex("""[^\p{L}\p{N}._ -]+""")
        private val INSTANCE_DIR = Regex("[0-9a-f]{32}")
        private val OWNED_FILE = Regex("[0-9a-f]{32}\\.(xlsx|docx|pdf)")
        private const val LIVE_FILE = ".live"
        private const val CLEANUP_LOCK = ".cleanup.lock"
        private val sweepMonitor = Any()
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

/** 一个码点选一个已嵌入字体。都画不出时换成 □ 或 ?，并记下 omitted。 */
private class PdfGlyphFonts(
    val primary: PDFont,
    private val choose: (String) -> PDFont?,
) {
    var omitted: Boolean = false
        private set

    fun take(glyph: String): Pair<PDFont, String> {
        choose(glyph)?.let { return it to glyph }
        omitted = true
        for (mark in listOf("□", "?")) {
            choose(mark)?.let { return it to mark }
        }
        throw ApiException.badRequest("pdf font is unavailable")
    }
}

/** getStringWidth 抛异常表示这个字体没有该字形。 */
private fun measures(font: PDFont, glyph: String): Boolean =
    try {
        font.getStringWidth(glyph)
        true
    } catch (e: Exception) {
        false
    }

/** cmap 里 glyph id 为 0 表示 .notdef，不能当成这个字符存在。 */
private fun covers(face: TrueTypeFont, codePoint: Int): Boolean =
    try {
        face.getUnicodeCmapLookup(false).getGlyphId(codePoint) != 0
    } catch (e: Exception) {
        false
    }

/** A4 纵向。表格里高于一页的行按行高切片，不把整行裁掉。 */
private class PdfLayout(
    private val document: PDDocument,
    private val fonts: PdfGlyphFonts,
) : AutoCloseable {
    val omitted: Boolean get() = fonts.omitted
    private val pageWidth = PDRectangle.A4.width
    private val pageHeight = PDRectangle.A4.height
    private var page = PDPage(PDRectangle.A4)
    private var stream = PDPageContentStream(document, page)
    private var y = pageHeight - DocumentExportService.PDF_MARGIN

    init {
        document.addPage(page)
    }

    fun gap(amount: Float) {
        ensure(amount)
        y -= amount
    }

    fun writeWrapped(text: String, size: Float) {
        val width = pageWidth - DocumentExportService.PDF_MARGIN * 2
        for (line in wrap(text, size, width)) {
            ensure(size + 4f)
            var x = DocumentExportService.PDF_MARGIN
            for (run in line) {
                drawText(run.font, run.text, size, x, y - size)
                x += textWidth(run.font, run.text, size)
            }
            y -= size + 4f
        }
    }

    fun writeTable(headers: List<String>, rows: List<List<String>>, size: Float) {
        val columns = headers.size
        val tableWidth = pageWidth - DocumentExportService.PDF_MARGIN * 2
        val columnWidth = tableWidth / columns
        drawRow(headers, size, columnWidth, header = true)
        for (row in rows) {
            drawRow(List(columns) { index -> row.getOrElse(index) { "" } }, size, columnWidth, header = false)
        }
        gap(8f)
    }

    private fun drawRow(cells: List<String>, size: Float, columnWidth: Float, header: Boolean) {
        val wrapped = cells.map { wrap(it, size, columnWidth - 6f) }
        val lineHeight = size + 3f
        val cursors = IntArray(wrapped.size)
        var firstSlice = true
        while (true) {
            val pending = wrapped.indices.any { cursors[it] < wrapped[it].size }
            if (!pending) break
            var capacity = linesThatFit(lineHeight)
            if (capacity < 1) {
                newPage()
                capacity = linesThatFit(lineHeight)
                if (capacity < 1) break
            }
            val slice = wrapped.mapIndexed { index, lines ->
                val from = cursors[index]
                val to = (from + capacity).coerceAtMost(lines.size)
                cursors[index] = to
                if (from >= lines.size) emptyList() else lines.subList(from, to)
            }
            paintRowSlice(slice, lineHeight, size, columnWidth, header && firstSlice)
            firstSlice = false
        }
    }

    private fun linesThatFit(lineHeight: Float): Int {
        val available = y - DocumentExportService.PDF_MARGIN
        return ((available - 6f) / lineHeight).toInt()
    }

    private fun paintRowSlice(
        slice: List<List<List<TextRun>>>,
        lineHeight: Float,
        size: Float,
        columnWidth: Float,
        header: Boolean,
    ) {
        val drawn = slice.maxOf { it.size }.coerceAtLeast(1)
        val rowHeight = drawn * lineHeight + 6f
        val top = y
        if (header) {
            stream.setNonStrokingColor(0.9f, 0.9f, 0.9f)
            stream.addRect(DocumentExportService.PDF_MARGIN, top - rowHeight, columnWidth * slice.size, rowHeight)
            stream.fill()
            stream.setNonStrokingColor(0f, 0f, 0f)
        }
        stream.setStrokingColor(0.6f, 0.6f, 0.6f)
        slice.forEachIndexed { index, lines ->
            val x = DocumentExportService.PDF_MARGIN + columnWidth * index
            stream.addRect(x, top - rowHeight, columnWidth, rowHeight)
            stream.stroke()
            var textY = top - 4f - size
            for (line in lines) {
                var runX = x + 3f
                for (run in line) {
                    drawText(run.font, run.text, size, runX, textY)
                    runX += textWidth(run.font, run.text, size)
                }
                textY -= lineHeight
            }
        }
        y -= rowHeight
    }

    private data class TextRun(val font: PDFont, val text: String)

    private fun wrap(text: String, size: Float, maxWidth: Float): List<List<TextRun>> {
        val lines = mutableListOf<List<TextRun>>()
        for (paragraph in normalizePdfText(text).split('\n')) {
            lines += wrapParagraph(paragraph, size, maxWidth)
        }
        return lines.ifEmpty { listOf(emptyList()) }
    }

    /** 按码点换行，同一行里相邻且同字体的字符合并成一段。 */
    private fun wrapParagraph(text: String, size: Float, maxWidth: Float): List<List<TextRun>> {
        if (text.isEmpty()) return listOf(emptyList())
        val lines = mutableListOf<MutableList<TextRun>>()
        var width = 0f
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            val (font, glyph) = fonts.take(String(Character.toChars(codePoint)))
            val glyphWidth = textWidth(font, glyph, size)
            if (lines.isEmpty() || (lines.last().isNotEmpty() && width + glyphWidth > maxWidth)) {
                lines.add(mutableListOf())
                width = 0f
            }
            val line = lines.last()
            val last = line.lastOrNull()
            if (last != null && last.font === font) {
                line[line.lastIndex] = TextRun(font, last.text + glyph)
            } else {
                line.add(TextRun(font, glyph))
            }
            width += glyphWidth
        }
        return lines
    }

    /** PDF 字体没有换行和制表符的字形，先收成换行和空格。 */
    private fun normalizePdfText(text: String): String = buildString(text.length) {
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val count = Character.charCount(codePoint)
            when (codePoint) {
                '\r'.code -> {
                    val next = offset + count
                    if (next < text.length && text.codePointAt(next) == '\n'.code) {
                        offset = next + Character.charCount('\n'.code)
                    } else {
                        offset += count
                    }
                    append('\n')
                }
                '\n'.code -> {
                    append('\n')
                    offset += count
                }
                '\t'.code -> {
                    append("    ")
                    offset += count
                }
                else -> {
                    if (!Character.isISOControl(codePoint)) appendCodePoint(codePoint)
                    offset += count
                }
            }
        }
    }

    private fun drawText(font: PDFont, text: String, size: Float, x: Float, textY: Float) {
        if (text.isEmpty()) return
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, textY)
        stream.showText(text)
        stream.endText()
    }

    private fun textWidth(font: PDFont, text: String, size: Float): Float =
        font.getStringWidth(text) / 1000f * size

    private fun ensure(height: Float) {
        if (y - height >= DocumentExportService.PDF_MARGIN) return
        newPage()
    }

    private fun newPage() {
        stream.close()
        page = PDPage(PDRectangle.A4)
        document.addPage(page)
        stream = PDPageContentStream(document, page)
        y = pageHeight - DocumentExportService.PDF_MARGIN
    }

    override fun close() {
        stream.close()
    }
}

private data class StoredExport(
    val tokenHash: ByteArray,
    val path: Path,
    val filename: String,
    val mediaType: String,
    val expiresAt: Instant,
)
