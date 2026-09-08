package com.lectern.core

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/**
 * A document → sections: one section per chapter where the format can tell us
 * (PDF outline, EPUB table of contents, Markdown headings); otherwise a single
 * untitled section. Paragraph breaks survive as blank lines, because the
 * tokenizer pauses on them.
 */
@Serializable
data class Section(val title: String, val text: String)

/** What a parser hands back: the text, and a cover if the format carried one. */
class Document(
    val sections: List<Section>,
    val cover: ByteArray? = null,
    /** A title from the file's own metadata, better than its file name. */
    val title: String? = null,
)

class UnreadableDocument(message: String) : Exception(message)

private val HEADING = Regex("""^#{1,3}\s+(.+?)\s*#*\s*$""")

/** Markdown-style headings (# through ###) become chapter titles. */
fun sectionsFromText(text: String): List<Section> {
    val sections = ArrayList<Section>()
    var title = ""
    val body = StringBuilder()
    for (line in text.replace(Regex("""\r\n?"""), "\n").split("\n")) {
        val h = HEADING.find(line)
        if (h != null) {
            if (body.isNotBlank()) sections += Section(title, body.toString())
            title = h.groupValues[1]
            body.setLength(0)
        } else {
            body.append(line).append('\n')
        }
    }
    if (body.isNotBlank()) sections += Section(title, body.toString())
    return sections.ifEmpty { listOf(Section("", text)) }
}

/** Reads [uri] into a document, picking a parser by MIME type and file name. */
suspend fun extractDocument(
    resolver: ContentResolver,
    uri: Uri,
    cacheDir: File,
): Document = withContext(Dispatchers.IO) {
    val name = displayName(resolver, uri).lowercase()
    val mime = resolver.getType(uri).orEmpty()

    fun open() = resolver.openInputStream(uri) ?: throw UnreadableDocument("Couldn't open that file")

    /** Formats read by entry name need a seekable copy on disk. */
    fun <T> withTempCopy(suffix: String, block: (File) -> T): T {
        val temp = File.createTempFile("import", suffix, cacheDir)
        return try {
            open().use { input -> temp.outputStream().use { input.copyTo(it) } }
            block(temp)
        } finally {
            temp.delete()
        }
    }

    val document = when {
        name.endsWith(".pdf") || mime == "application/pdf" ->
            open().use { extractPdf(it, cacheDir) }

        name.endsWith(".epub") || mime == "application/epub+zip" ->
            withTempCopy(".epub") { extractEpub(it) }

        name.endsWith(".docx") ||
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
            withTempCopy(".docx") { extractDocx(it) }

        name.endsWith(".fb2") -> open().use { extractFb2(it.reader().readText()) }

        name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml") ||
            mime == "text/html" ->
            open().use { extractHtmlDocument(it.reader().readText(), "") }

        else -> Document(sectionsFromText(open().use { it.reader().readText() }))
    }

    if (document.sections.none { it.text.isNotBlank() }) {
        throw UnreadableDocument("No text found in this file")
    }
    document
}

fun displayName(resolver: ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) return c.getString(0)
    }
    return uri.lastPathSegment ?: "Untitled"
}

private val EXTENSION = Regex("""\.(pdf|epub|txt|md|text|docx|fb2|html?|xhtml)$""", RegexOption.IGNORE_CASE)

fun titleFromFileName(name: String): String =
    name.replace(EXTENSION, "").trim().ifBlank { "Untitled" }
