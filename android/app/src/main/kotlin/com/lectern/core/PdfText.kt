package com.lectern.core

import android.graphics.Bitmap
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Page text plus, where the file has bookmarks, one section per bookmark. */
internal fun extractPdf(input: InputStream, tempDir: File): Document {
    // Big books spill to disk rather than taking the heap down with them.
    val memory = MemoryUsageSetting.setupMixed(24L * 1024 * 1024).setTempDir(tempDir)
    PDDocument.load(input, memory).use { doc ->
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        val pageTexts = (1..doc.numberOfPages).map { p ->
            stripper.startPage = p
            stripper.endPage = p
            reflow(stripper.getText(doc))
        }

        val cover = renderCover(doc)
        val title = doc.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() }

        val marks = outlineMarks(doc)
        val sections = ArrayList<Section>()
        for ((i, mark) in marks.withIndex()) {
            val end = if (i + 1 < marks.size) marks[i + 1].page else pageTexts.size
            val text = pageTexts.subList(mark.page, end)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            if (text.isNotBlank()) sections += Section(mark.title, text)
        }
        if (sections.isNotEmpty()) return Document(sections, cover, title)

        val text = pageTexts.filter { it.isNotBlank() }.joinToString("\n\n")
        if (text.isBlank()) throw UnreadableDocument("No text found in this PDF")
        return Document(listOf(Section("", text)), cover, title)
    }
}

/** The first page, small, as the book's cover. Failure here is not fatal. */
private fun renderCover(doc: PDDocument): ByteArray? = runCatching {
    if (doc.numberOfPages == 0) return null
    val page = PDFRenderer(doc).renderImageWithDPI(0, COVER_DPI)
    val scale = COVER_WIDTH.toFloat() / page.width
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(page, COVER_WIDTH, (page.height * scale).toInt(), true)
    } else {
        page
    }
    ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        out.toByteArray()
    }
}.getOrNull()

private const val COVER_DPI = 72f
private const val COVER_WIDTH = 480

private class Mark(val title: String, val page: Int)

/** Top-level bookmarks resolved to page indexes. Broken outlines are common. */
private fun outlineMarks(doc: PDDocument): List<Mark> = try {
    val marks = ArrayList<Mark>()
    var item: PDOutlineItem? = doc.documentCatalog?.documentOutline?.firstChild
    while (item != null) {
        val current = item
        val page = runCatching { current.findDestinationPage(doc) }.getOrNull()
        if (page != null) {
            val index = doc.pages.indexOf(page)
            if (index >= 0) {
                marks += Mark(current.title?.trim().orEmpty().ifBlank { "Untitled" }, index)
            }
        }
        item = current.nextSibling
    }
    marks.sortBy { it.page }
    val deduped = marks.filterIndexed { i, m -> i == 0 || m.page > marks[i - 1].page }
    when {
        deduped.isEmpty() -> emptyList()
        deduped.first().page > 0 -> listOf(Mark("Front matter", 0)) + deduped
        else -> deduped
    }
} catch (_: Exception) {
    emptyList()
}

private val SENTENCE_TAIL = Regex("""[.!?…]["')\]»”’]*$""")

/**
 * PDF text arrives as hard-wrapped lines with no paragraph marks. Re-join them
 * into paragraphs: de-hyphenate words broken across a line, and start a new
 * paragraph where a short line ends a sentence — which is what the last line of
 * a paragraph looks like.
 */
internal fun reflow(pageText: String): String {
    val lines = pageText.replace("\r\n", "\n").split("\n").map { it.trim() }
    val widths = lines.filter { it.isNotEmpty() }.map { it.length }
    if (widths.isEmpty()) return ""
    val typical = widths.sorted()[widths.size / 2]

    val out = StringBuilder()
    var pendingBreak = false
    var glue = false // previous line ended mid-word with a hyphen
    for (line in lines) {
        if (line.isEmpty()) {
            if (out.isNotEmpty()) pendingBreak = true
            continue
        }
        if (out.isNotEmpty()) {
            out.append(if (pendingBreak) "\n\n" else if (glue) "" else " ")
        }
        pendingBreak = false
        glue = false
        if (line.length > 1 && line.endsWith("-") && !line.endsWith("--")) {
            out.append(line.dropLast(1))
            glue = true
        } else {
            out.append(line)
            val short = line.length < typical * 0.75
            if (short && SENTENCE_TAIL.containsMatchIn(line)) pendingBreak = true
        }
    }
    return out.toString()
}
