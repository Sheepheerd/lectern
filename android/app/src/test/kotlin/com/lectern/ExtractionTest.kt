package com.lectern

import com.lectern.core.extractDocx
import com.lectern.core.extractFb2
import com.lectern.core.extractHtmlDocument
import com.lectern.core.normalizeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractionTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ---------- HTML / articles ----------

    private val page = """
        <html><head><title>Site — The Lamp</title>
        <meta property="og:title" content="The Lamp"></head>
        <body>
          <nav><a href="/">Home</a><a href="/about">About</a></nav>
          <aside><p>Subscribe to our newsletter for more of this sort of thing today.</p></aside>
          <article>
            <h1>The Lamp</h1>
            <p>Mr. Aldridge kept the lamp burning until dawn, and read the way other people run.</p>
            <p>The trick, he said, was not to move your eyes at all. Let the words come to you.</p>
            <h2>The Rail</h2>
            <p>By 1897 the Post Office had adopted the practice, which sounded impossible, and was.</p>
          </article>
          <footer><p>Copyright somebody, all rights reserved, every year since the beginning.</p></footer>
        </body></html>
    """.trimIndent()

    @Test
    fun `an article keeps its prose and drops the chrome`() {
        val doc = extractHtmlDocument(page, "https://example.com/lamp")
        val text = doc.sections.joinToString("\n") { it.text }
        assertTrue(text.contains("Mr. Aldridge kept the lamp"))
        assertFalse(text.contains("Subscribe to our newsletter"))
        assertFalse(text.contains("Copyright somebody"))
        assertFalse(text.contains("Home"))
    }

    @Test
    fun `headings in an article become sections`() {
        val doc = extractHtmlDocument(page, "https://example.com/lamp")
        assertEquals(listOf("The Lamp", "The Rail"), doc.sections.map { it.title })
    }

    @Test
    fun `the article title prefers the page's own metadata`() {
        assertEquals("The Lamp", extractHtmlDocument(page, "").title)
    }

    @Test
    fun `a page with no article container still gives up its text`() {
        val bare = "<html><body><p>${"A sentence that is long enough to count. ".repeat(20)}</p></body></html>"
        val doc = extractHtmlDocument(bare, "")
        assertTrue(doc.sections.first().text.contains("long enough to count"))
    }

    @Test
    fun `bare hosts are given a scheme`() {
        assertEquals("https://example.com/x", normalizeUrl("example.com/x"))
        assertEquals("http://example.com", normalizeUrl("http://example.com"))
        assertEquals("https://example.com", normalizeUrl("  https://example.com  "))
    }

    // ---------- FictionBook ----------

    @Test
    fun `fb2 sections become chapters`() {
        val fb2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook><description><title-info>
              <book-title>The Lamp</book-title>
            </title-info></description>
            <body>
              <section><title><p>One</p></title><p>First paragraph here.</p><p>Second one.</p></section>
              <section><title><p>Two</p></title><p>Another chapter entirely.</p></section>
            </body>
            <body name="notes"><section><p>A footnote nobody reads.</p></section></body>
            </FictionBook>
        """.trimIndent()

        val doc = extractFb2(fb2)
        assertEquals("The Lamp", doc.title)
        assertEquals(listOf("One", "Two"), doc.sections.map { it.title })
        assertTrue(doc.sections[0].text.contains("First paragraph here."))
        assertTrue(doc.sections[0].text.contains("\n\n"))
        assertFalse(doc.sections.any { it.text.contains("footnote") })
    }

    // ---------- DOCX ----------

    private fun docx(documentXml: String): File {
        val file = temp.newFile("test.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray())
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `docx paragraphs and headings survive`() {
        val file = docx(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter One</w:t></w:r></w:p>
                <w:p><w:r><w:t>The lamp </w:t></w:r><w:r><w:t>burned until dawn.</w:t></w:r></w:p>
                <w:p><w:r><w:t>A second paragraph.</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Chapter Two</w:t></w:r></w:p>
                <w:p><w:r><w:t>And on it went.</w:t></w:r></w:p>
              </w:body>
            </w:document>
            """.trimIndent()
        )

        val doc = extractDocx(file)
        assertEquals(listOf("Chapter One", "Chapter Two"), doc.sections.map { it.title })
        // Runs inside one paragraph join up rather than becoming separate ones.
        assertTrue(doc.sections[0].text.startsWith("The lamp burned until dawn."))
        assertTrue(doc.sections[0].text.contains("A second paragraph."))
    }

    @Test
    fun `a docx without headings is one section`() {
        val file = docx(
            """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>Just some text.</w:t></w:r></w:p></w:body>
            </w:document>
            """.trimIndent()
        )
        val doc = extractDocx(file)
        assertEquals(1, doc.sections.size)
        assertEquals("", doc.sections[0].title)
    }
}
