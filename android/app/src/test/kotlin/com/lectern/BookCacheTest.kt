package com.lectern

import com.lectern.core.BookCache
import com.lectern.core.Section
import com.lectern.core.tokenize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class BookCacheTest {

    private val cache = BookCache()

    private fun book(text: String) = tokenize(listOf(Section("", text)))

    @Test
    fun `returns the book it was given`() {
        val a = book("one two three.")
        cache.put("b1", a)
        assertSame(a, cache.get("b1"))
    }

    @Test
    fun `misses for a book it has not seen`() {
        cache.put("b1", book("one two three."))
        assertNull(cache.get("b2"))
    }

    @Test
    fun `holds one book at a time`() {
        val a = book("one two three.")
        val b = book("four five six.")
        cache.put("b1", a)
        cache.put("b2", b)
        assertNull("the older book should have been let go", cache.get("b1"))
        assertSame(b, cache.get("b2"))
    }

    @Test
    fun `forgets a book that is deleted`() {
        cache.put("b1", book("one two three."))
        cache.forget("b1")
        assertNull(cache.get("b1"))
    }

    @Test
    fun `forgetting another book leaves the held one alone`() {
        cache.put("b1", book("one two three."))
        cache.forget("b2")
        assertNotNull(cache.get("b1"))
    }

    @Test
    fun `clear drops everything`() {
        cache.put("b1", book("one two three."))
        cache.clear()
        assertNull(cache.get("b1"))
    }

    @Test
    fun `a cached book keeps its chapters and paragraphs`() {
        val a = book("one two three.\n\nfour five six.")
        cache.put("b1", a)
        val again = cache.get("b1")!!
        assertEquals(a.chapters, again.chapters)
        assertEquals(a.paragraphs, again.paragraphs)
    }
}
