package com.lectern.core

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/**
 * DOCX: a zip whose word/document.xml holds paragraphs as <w:p>, with the text
 * in <w:t> runs. Headings carry a style name we can use for chapter titles.
 */
internal fun extractDocx(file: File): Document {
    ZipFile(file).use { zip ->
        val entry = zip.getEntry("word/document.xml")
            ?: throw UnreadableDocument("Not a valid DOCX (no document.xml)")
        val xml = zip.getInputStream(entry).use { it.reader().readText() }
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())

        val sections = ArrayList<Section>()
        var title = ""
        val body = StringBuilder()

        for (paragraph in doc.select("w|p, p")) {
            // wholeText keeps the spaces between runs; text() would trim them,
            // gluing "The lamp " and "burned" into one word.
            val text = paragraph.select("w|t, t").joinToString("") { it.wholeText() }.squish()
            val style = paragraph.selectFirst("w|pStyle, pStyle")?.attr("w:val").orEmpty()
            val isHeading = style.startsWith("Heading", ignoreCase = true) ||
                style.equals("Title", ignoreCase = true)

            if (isHeading && text.isNotEmpty()) {
                if (body.isNotBlank()) sections += Section(title, body.toString())
                title = text
                body.setLength(0)
            } else if (text.isNotEmpty()) {
                body.append(text).append("\n\n")
            }
        }
        if (body.isNotBlank()) sections += Section(title, body.toString())
        if (sections.isEmpty()) throw UnreadableDocument("No text found in this document")

        val docTitle = runCatching {
            zip.getEntry("docProps/core.xml")?.let { core ->
                val props = Jsoup.parse(
                    zip.getInputStream(core).use { it.reader().readText() },
                    "",
                    Parser.xmlParser(),
                )
                props.selectFirst("dc|title, title")?.text()?.squish()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()

        return Document(sections, title = docTitle)
    }
}

/**
 * FictionBook: XML with <body><section>, paragraphs in <p> and titles in
 * <title>. Notes and binaries are skipped.
 */
internal fun extractFb2(xml: String): Document {
    val doc = Jsoup.parse(xml, "", Parser.xmlParser())
    val bookTitle = doc.selectFirst("book-title")?.text()?.squish()?.takeIf { it.isNotEmpty() }

    val sections = ArrayList<Section>()
    val bodies = doc.select("body").filter { it.attr("name") != "notes" }
    for (body in bodies) {
        val parts = body.select("section")
        val scopes = parts.ifEmpty { listOf(body) }
        for (section in scopes) {
            // A nested section's text belongs to the innermost one only.
            if (section.parents().any { it.tagName() == "section" }) continue
            val title = section.selectFirst("title")?.text()?.squish().orEmpty()
            val text = section.select("p")
                .map { it.text().squish() }
                .filter { it.isNotEmpty() }
                .joinToString("\n\n")
            if (text.isNotBlank()) sections += Section(title, text)
        }
    }
    if (sections.isEmpty()) throw UnreadableDocument("No text found in this FictionBook")

    val cover = runCatching {
        val id = doc.selectFirst("coverpage image")
            ?.let { it.attr("l:href").ifEmpty { it.attr("xlink:href") } }
            ?.removePrefix("#")
        id?.let { binaryId ->
            doc.select("binary").firstOrNull { it.attr("id") == binaryId }
                ?.text()
                ?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
        }
    }.getOrNull()

    return Document(sections, cover, bookTitle)
}
