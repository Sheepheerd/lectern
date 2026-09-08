package com.lectern.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reading an article off the web. The page is fetched, then reduced to the one
 * block of prose it is actually about — no reader service, no account, and the
 * fetch is the only time Lectern touches the network.
 */
suspend fun fetchArticle(rawUrl: String): Document = withContext(Dispatchers.IO) {
    val url = normalizeUrl(rawUrl)
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
    }
    val html = try {
        val code = connection.responseCode
        if (code !in 200..299) throw UnreadableDocument("The page answered with $code")
        val stream = connection.inputStream
        val charset = connection.contentEncoding
            ?: connection.contentType?.substringAfter("charset=", "")?.takeIf { it.isNotBlank() }
        stream.use { it.reader(charsetOrUtf8(charset)).readText() }
    } finally {
        connection.disconnect()
    }
    extractHtmlDocument(html, url)
}

private fun charsetOrUtf8(name: String?) =
    runCatching { charset(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)

internal fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
    else "https://$trimmed"
}

private const val USER_AGENT =
    "Mozilla/5.0 (Android) Lectern/1.0 (reader; local text extraction)"

/**
 * Turns a page into readable sections. Everything that isn't prose is dropped,
 * then the densest remaining block wins: the container holding the most
 * paragraph text is almost always the article itself.
 */
internal fun extractHtmlDocument(html: String, baseUrl: String): Document {
    val doc = Jsoup.parse(html, baseUrl)
    val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.squish()
        ?.takeIf { it.isNotEmpty() }
        ?: doc.selectFirst("h1")?.text()?.squish()?.takeIf { it.isNotEmpty() }
        ?: doc.title().squish().takeIf { it.isNotEmpty() }

    doc.select(
        "script, style, noscript, nav, header, footer, aside, form, iframe, svg, " +
            "figure figcaption, .advert, .ad, .share, .social, .comments, #comments"
    ).remove()

    val body = doc.body() ?: throw UnreadableDocument("That page had no readable body")
    val candidate = bestCandidate(body) ?: body

    val sections = ArrayList<Section>()
    var heading = ""
    val paragraph = StringBuilder()
    for (element in candidate.select("h1, h2, h3, p, li, blockquote, pre")) {
        val text = element.text().squish()
        if (text.isEmpty()) continue
        when (element.tagName()) {
            "h1", "h2", "h3" -> {
                if (paragraph.isNotBlank()) sections += Section(heading, paragraph.toString())
                heading = text
                paragraph.setLength(0)
            }

            else -> if (text.length >= MIN_PARAGRAPH) paragraph.append(text).append("\n\n")
        }
    }
    if (paragraph.isNotBlank()) sections += Section(heading, paragraph.toString())

    if (sections.none { it.text.isNotBlank() }) {
        val fallback = body.text().squish()
        if (fallback.isBlank()) throw UnreadableDocument("No article text found on that page")
        return Document(listOf(Section("", fallback)), title = title)
    }
    return Document(sections, title = title)
}

/** The element whose own paragraphs carry the most text. */
private fun bestCandidate(body: Element): Element? {
    var best: Element? = null
    var bestScore = 0

    val containers = body.select("article, main, [role=main], section, div")
    for (element in containers) {
        var score = 0
        for (p in element.select("p")) {
            val length = p.text().length
            if (length >= MIN_PARAGRAPH) score += length
        }
        // Prefer the innermost container with the same text: it has less chrome.
        if (score > bestScore || (score == bestScore && score > 0 && element.parents().contains(best))) {
            best = element
            bestScore = score
        }
    }
    return if (bestScore >= MIN_ARTICLE) best else null
}

private const val MIN_PARAGRAPH = 25
private const val MIN_ARTICLE = 400
