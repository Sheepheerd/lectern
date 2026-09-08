package com.lectern.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document as JsoupDocument
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** One section per spine document, titled from the table of contents. */
internal fun extractEpub(file: File): Document {
    ZipFile(file).use { zip ->
        val containerXml = zip.text("META-INF/container.xml")
            ?: throw UnreadableDocument("Not a valid EPUB (missing container.xml)")
        val opfPath = Jsoup.parse(containerXml, "", Parser.xmlParser())
            .selectFirst("rootfile")?.attr("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: throw UnreadableDocument("Not a valid EPUB (no rootfile)")

        val opf = Jsoup.parse(
            zip.text(opfPath) ?: throw UnreadableDocument("Not a valid EPUB (no package document)"),
            "",
            Parser.xmlParser(),
        )

        val manifest = HashMap<String, String>() // item id -> zip path
        val properties = HashMap<String, String>() // item id -> properties
        var navPath: String? = null
        for (item in opf.select("manifest > item")) {
            val id = item.attr("id")
            val path = resolvePath(opfPath, item.attr("href")) ?: continue
            manifest[id] = path
            properties[id] = item.attr("properties")
            if (item.attr("properties").split(Regex("""\s+""")).contains("nav")) navPath = path
        }

        val titles = tocTitles(zip, opf, manifest, navPath)
        val cover = coverImage(zip, opf, manifest, properties)
        val title = opf.selectFirst("metadata > dc|title, metadata > title")
            ?.text()?.squish()?.takeIf { it.isNotEmpty() }

        val sections = ArrayList<Section>()
        for (ref in opf.select("spine > itemref")) {
            val path = manifest[ref.attr("idref")] ?: continue
            val html = zip.text(path) ?: continue
            val doc = Jsoup.parse(html, "")
            doc.select("script, style, nav").remove()

            // Block elements become paragraphs so pauses land between them.
            val blocks = doc.body().select("p, h1, h2, h3, h4, h5, h6, li, blockquote, td, dt, dd")
            val text = if (blocks.isNotEmpty()) {
                blocks.map { it.text().trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
            } else {
                doc.body().text().trim()
            }
            if (text.isBlank()) continue

            val sectionTitle = titles[path]
                ?: doc.selectFirst("h1, h2, h3")?.text()?.squish().orEmpty()
            sections += Section(sectionTitle, text)
        }
        if (sections.isEmpty()) throw UnreadableDocument("No readable text found in this EPUB")
        return Document(sections, cover, title)
    }
}

/** The cover image: EPUB3 declares it in properties, EPUB2 in a meta tag. */
private fun coverImage(
    zip: ZipFile,
    opf: JsoupDocument,
    manifest: Map<String, String>,
    properties: Map<String, String>,
): ByteArray? {
    val byProperty = properties.entries
        .firstOrNull { it.value.split(Regex("""\s+""")).contains("cover-image") }
        ?.key
    val byMeta = opf.selectFirst("metadata > meta[name=cover]")?.attr("content")
    val path = listOfNotNull(byProperty, byMeta).firstNotNullOfOrNull { manifest[it] }
        ?: return null
    val entry = zip.getEntry(path) ?: return null
    if (entry.size > MAX_COVER_BYTES) return null
    return runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull()
}

private const val MAX_COVER_BYTES = 4L * 1024 * 1024

/** Chapter titles from the EPUB3 nav document, falling back to the EPUB2 NCX. */
private fun tocTitles(
    zip: ZipFile,
    opf: JsoupDocument,
    manifest: Map<String, String>,
    navPath: String?,
): Map<String, String> {
    val titles = LinkedHashMap<String, String>()

    if (navPath != null) {
        zip.text(navPath)?.let { navHtml ->
            val nav = Jsoup.parse(navHtml, "")
            val scope = nav.select("nav").firstOrNull { el ->
                el.attr("epub:type").contains("toc") || el.attr("role") == "doc-toc"
            } ?: nav
            for (a in scope.select("a[href]")) {
                val path = resolvePath(navPath, a.attr("href")) ?: continue
                val title = a.text().squish()
                if (title.isNotEmpty()) titles.putIfAbsent(path, title)
            }
        }
    }

    if (titles.isEmpty()) {
        val ncxPath = opf.selectFirst("spine")?.attr("toc")?.let { manifest[it] }
        val ncxXml = ncxPath?.let { zip.text(it) }
        if (ncxPath != null && ncxXml != null) {
            val ncx = Jsoup.parse(ncxXml, "", Parser.xmlParser())
            for (point in ncx.select("navPoint")) {
                val src = point.selectFirst("content")?.attr("src") ?: continue
                val title = point.selectFirst("navLabel > text")?.text()?.squish() ?: continue
                val path = resolvePath(ncxPath, src) ?: continue
                if (title.isNotEmpty()) titles.putIfAbsent(path, title)
            }
        }
    }
    return titles
}

/** Resolves [href] against the directory of [fromPath], dropping any fragment. */
internal fun resolvePath(fromPath: String, href: String): String? {
    if (href.isBlank()) return null
    val clean = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        .substringBefore('#')
    if (clean.isEmpty()) return null
    val base = fromPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val out = ArrayList<String>()
    for (part in (base + clean).split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
            else -> out += part
        }
    }
    return out.joinToString("/").takeIf { it.isNotEmpty() }
}

private fun ZipFile.text(path: String): String? {
    val entry = getEntry(path) ?: return null
    return getInputStream(entry).use { it.reader().readText() }
}

internal fun String.squish() = replace(Regex("""\s+"""), " ").trim()
