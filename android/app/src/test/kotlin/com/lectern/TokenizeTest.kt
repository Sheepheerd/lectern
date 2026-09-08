package com.lectern

import com.lectern.core.Punct
import com.lectern.core.Section
import com.lectern.core.orpIndex
import com.lectern.core.reflow
import com.lectern.core.resolvePath
import com.lectern.core.sectionsFromText
import com.lectern.core.sentenceStartAfter
import com.lectern.core.sentenceStartBefore
import com.lectern.core.tokenize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenizeTest {

    private fun sentenceEnds(text: String): List<String> =
        tokenize(listOf(Section("", text)))
            .tokens
            .filter { it.punct == Punct.SENTENCE }
            .map { it.text }

    @Test
    fun `full stops end sentences`() {
        assertEquals(listOf("home.", "again!"), sentenceEnds("She went home. She left again!"))
    }

    @Test
    fun `titles are not sentence ends`() {
        assertEquals(listOf("late."), sentenceEnds("Mr. Smith and Dr. Jones were late."))
    }

    @Test
    fun `initials are not sentence ends`() {
        assertEquals(listOf("it."), sentenceEnds("J. R. R. Tolkien wrote it."))
    }

    @Test
    fun `initialisms end a sentence only before a capital`() {
        assertEquals(emptyList<String>(), sentenceEnds("The U.S. army marched"))
        assertEquals(listOf("U.S.", "marched."), sentenceEnds("He left the U.S. Then he marched."))
    }

    @Test
    fun `question and exclamation marks always end sentences`() {
        assertEquals(listOf("etc.?"), sentenceEnds("Did he pack socks, shoes, etc.?"))
    }

    @Test
    fun `clause punctuation is marked but does not end a sentence`() {
        val tokens = tokenize(listOf(Section("", "First, second; third."))).tokens
        assertEquals(Punct.CLAUSE, tokens[0].punct)
        assertEquals(Punct.CLAUSE, tokens[1].punct)
        assertEquals(Punct.SENTENCE, tokens[2].punct)
    }

    @Test
    fun `pivot sits left of centre and ignores trailing punctuation`() {
        assertEquals(0, orpIndex("I"))
        assertEquals(1, orpIndex("word"))
        assertEquals(1, orpIndex("word."))
        assertEquals(2, orpIndex("sentence"))
        assertEquals(4, orpIndex("incomprehensible"))
    }

    @Test
    fun `weights grow with length and digits`() {
        val tokens = tokenize(listOf(Section("", "cat extraordinary 1999"))).tokens
        assertTrue(tokens[1].weight > tokens[0].weight)
        assertTrue(tokens[2].weight > tokens[0].weight)
    }

    @Test
    fun `sections become chapters and blank lines become paragraphs`() {
        val book = tokenize(
            listOf(
                Section("One", "alpha beta\n\ngamma"),
                Section("Two", "delta"),
            )
        )
        assertEquals(listOf("One", "Two"), book.chapters.map { it.title })
        assertEquals(listOf(0, 3), book.chapters.map { it.start })
        assertEquals(3, book.paragraphs.size)
        assertTrue(book.tokens[1].paraEnd)
        assertFalse(book.tokens[0].paraEnd)
    }

    @Test
    fun `empty sections do not produce chapters`() {
        val book = tokenize(listOf(Section("Blank", "   \n\n  "), Section("Real", "word")))
        assertEquals(listOf("Real"), book.chapters.map { it.title })
    }

    @Test
    fun `sentence navigation lands on sentence starts`() {
        val tokens = tokenize(listOf(Section("", "One two. Three four. Five six."))).tokens
        assertEquals(2, sentenceStartAfter(tokens, 0))
        assertEquals(4, sentenceStartAfter(tokens, 2))
        assertEquals(2, sentenceStartBefore(tokens, 3))
        assertEquals(0, sentenceStartBefore(tokens, 1))
    }

    @Test
    fun `markdown headings become section titles`() {
        val sections = sectionsFromText("# Chapter One\nsome text\n\n## Chapter Two\nmore text\n")
        assertEquals(listOf("Chapter One", "Chapter Two"), sections.map { it.title })
        assertTrue(sections[0].text.contains("some text"))
    }

    @Test
    fun `text without headings is one untitled section`() {
        val sections = sectionsFromText("just a paragraph")
        assertEquals(1, sections.size)
        assertEquals("", sections[0].title)
    }

    @Test
    fun `pdf reflow rejoins wrapped lines and de-hyphenates`() {
        val page = """
            The quick brown fox jumped over the lazy dog and kept
            running until it reached the far side of the mead-
            ow at last.
            A new paragraph starts here and runs on for a while
            longer than the others do.
        """.trimIndent()
        val out = reflow(page)
        assertTrue(out.contains("meadow at last."))
        assertTrue(out.contains("\n\nA new paragraph"))
    }

    @Test
    fun `epub hrefs resolve against their document`() {
        assertEquals("OEBPS/text/ch1.xhtml", resolvePath("OEBPS/content.opf", "text/ch1.xhtml#top"))
        assertEquals("images/cover.xhtml", resolvePath("OEBPS/nav.xhtml", "../images/cover.xhtml"))
        assertEquals("content.opf", resolvePath("META-INF/container.xml", "../content.opf"))
        assertNull(resolvePath("OEBPS/nav.xhtml", "#fragment-only"))
    }
}
