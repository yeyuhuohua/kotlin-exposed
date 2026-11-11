package com.atguigu.hrspring.service

import com.atguigu.hrspring.common.api.ApiException
import org.apache.fontbox.ttf.OS2WindowsMetricsTable
import org.apache.fontbox.ttf.TrueTypeFont
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont

/** 一个码点选一个已嵌入字体。都画不出时换成 □ 或 ?，并记下 omitted。 */
internal class PdfGlyphFonts(
    val primary: PDFont,
    private val choose: (String) -> PDFont?,
) {
    var omitted: Boolean = false
        private set

    /** 为字符选择可嵌入字体；缺字时使用替代字符并记录。 */
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
internal fun measures(font: PDFont, glyph: String): Boolean =
    try {
        font.getStringWidth(glyph)
        true
    } catch (e: Exception) {
        false
    }

/** cmap 里 glyph id 为 0 表示 .notdef，不能当成这个字符存在。 */
internal fun covers(face: TrueTypeFont, codePoint: Int): Boolean =
    try {
        face.getUnicodeCmapLookup(false).getGlyphId(codePoint) != 0
    } catch (e: Exception) {
        false
    }

/** 与 PDFBox 的子集嵌入规则一致：受限、仅位图或禁止子集的字体跳过。 */
internal fun canEmbedSubset(face: TrueTypeFont): Boolean {
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

/** A4 纵向。表格里高于一页的行按行高切片，不把整行裁掉。 */
internal class PdfLayout(
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

    /** 在 PDF 当前位置插入垂直间距，必要时换页。 */
    fun gap(amount: Float) {
        ensure(amount)
        y -= amount
    }

    /** 按可用宽度换行并写入 PDF 文本。 */
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

    /** 写入 PDF 表头和数据行，并支持跨页行切片。 */
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

    /** 逐行排版表格单元格，按剩余页面空间切片。 */
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

    /** 计算当前页剩余空间可容纳的表格行数。 */
    private fun linesThatFit(lineHeight: Float): Int {
        val available = y - DocumentExportService.PDF_MARGIN
        return ((available - 6f) / lineHeight).toInt()
    }

    /** 绘制表格的一片行内容、边框和表头背景。 */
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

    /** 按段落和文本宽度拆分 PDF 行，并保留字体分段。 */
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

    /** 使用指定字体和坐标写入一段 PDF 文本。 */
    private fun drawText(font: PDFont, text: String, size: Float, x: Float, textY: Float) {
        if (text.isEmpty()) return
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, textY)
        stream.showText(text)
        stream.endText()
    }

    /** 计算文本在指定字体和字号下的宽度。 */
    private fun textWidth(font: PDFont, text: String, size: Float): Float =
        font.getStringWidth(text) / 1000f * size

    /** 确保当前页有足够高度，不足时新建页面。 */
    private fun ensure(height: Float) {
        if (y - height >= DocumentExportService.PDF_MARGIN) return
        newPage()
    }

    /** 关闭当前内容流并新建 A4 页面。 */
    private fun newPage() {
        stream.close()
        page = PDPage(PDRectangle.A4)
        document.addPage(page)
        stream = PDPageContentStream(document, page)
        y = pageHeight - DocumentExportService.PDF_MARGIN
    }

    /** 关闭当前资源并释放相关句柄。 */
    override fun close() {
        stream.close()
    }
}
