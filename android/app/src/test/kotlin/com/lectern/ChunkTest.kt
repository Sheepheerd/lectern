package com.lectern

import com.lectern.core.Section
import com.lectern.core.chunkAt
import com.lectern.core.tokenize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkTest {

    private fun tokens(text: String) = tokenize(listOf(Section("", text))).tokens

    @Test
    fun `a chunk of one is just the word`() {
        val words = tokens("one two three")
        val chunk = chunkAt(words, 1, 1)!!
        assertEquals("two", chunk.text)
        assertEquals(1, chunk.size)
        assertEquals(words[1].orp, chunk.orp)
    }

    @Test
    fun `larger chunks gather neighbours`() {
        val chunk = chunkAt(tokens("one two three four"), 0, 3)!!
        assertEquals("one two three", chunk.text)
        assertEquals(3, chunk.size)
    }

    @Test
    fun `a chunk stops at a full stop`() {
        val chunk = chunkAt(tokens("one two. three four"), 0, 3)!!
        assertEquals("one two.", chunk.text)
        assertEquals(2, chunk.size)
    }

    @Test
    fun `a chunk stops at the end of a paragraph`() {
        val chunk = chunkAt(tokens("one two\n\nthree four"), 0, 3)!!
        assertEquals("one two", chunk.text)
        assertEquals(2, chunk.size)
    }

    @Test
    fun `long words are not crammed together`() {
        val chunk = chunkAt(tokens("extraordinarily complicated business"), 0, 3)!!
        assertTrue(chunk.text.length <= 20)
        assertTrue(chunk.size < 3)
    }

    @Test
    fun `the pivot lands inside the middle word`() {
        val words = tokens("one two three")
        val chunk = chunkAt(words, 0, 3)!!
        // "one two three": the middle word starts at 4, and keeps its own pivot.
        assertEquals(4 + words[1].orp, chunk.orp)
        assertTrue(chunk.orp < chunk.text.length)
    }

    @Test
    fun `a single word always yields a chunk, and past the end yields none`() {
        val words = tokens("solitary")
        assertEquals("solitary", chunkAt(words, 0, 3)!!.text)
        assertNull(chunkAt(words, 5, 3))
        assertNull(chunkAt(emptyList(), 0, 1))
    }

    @Test
    fun `chunks tile the book without gaps or overlap`() {
        val words = tokens("One two three. Four five six seven. Eight nine.")
        var i = 0
        val seen = ArrayList<String>()
        while (i <= words.lastIndex) {
            val chunk = chunkAt(words, i, 3)!!
            assertEquals(i, chunk.start)
            seen += (i until i + chunk.size).map { words[it].text }
            i += chunk.size
        }
        assertEquals(words.map { it.text }, seen)
    }
}
