package com.lectern

import com.lectern.data.ShelfEntry
import com.lectern.data.ShelfOrder
import com.lectern.data.sortedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfOrderTest {

    // A book is opened when it is added, so openedAt is never older than
    // addedAt — except for "c", which was imported and left unread.
    private val shelf = listOf(
        ShelfEntry(id = "a", title = "Zeno", words = 100, index = 90, addedAt = 100, openedAt = 400),
        ShelfEntry(id = "b", title = "amble", words = 100, index = 10, addedAt = 200, openedAt = 300),
        ShelfEntry(id = "c", title = "Middle", words = 100, index = 50, addedAt = 350, openedAt = 0),
    )

    @Test
    fun `recent puts the last book you opened first`() {
        assertEquals(listOf("a", "c", "b"), shelf.sortedBy(ShelfOrder.Recent).map { it.id })
    }

    @Test
    fun `a book never opened falls back to when it was added`() {
        // "c" has never been opened but was imported after "b" was last read.
        val order = shelf.sortedBy(ShelfOrder.Recent).map { it.id }
        assertTrue(order.indexOf("c") < order.indexOf("b"))

        val fresh = ShelfEntry(id = "d", title = "New", addedAt = 500, openedAt = 0)
        assertEquals("d", (shelf + fresh).sortedBy(ShelfOrder.Recent).first().id)
    }

    @Test
    fun `added ignores reading and uses the import date`() {
        assertEquals(listOf("c", "b", "a"), shelf.sortedBy(ShelfOrder.Added).map { it.id })
    }

    @Test
    fun `title sorts without minding capitals`() {
        assertEquals(listOf("b", "c", "a"), shelf.sortedBy(ShelfOrder.Title).map { it.id })
    }

    @Test
    fun `least finished comes first by progress`() {
        assertEquals(listOf("b", "c", "a"), shelf.sortedBy(ShelfOrder.Progress).map { it.id })
    }

    @Test
    fun `progress and its flags describe where a book stands`() {
        val unread = ShelfEntry(id = "x", title = "x", words = 100, index = 0)
        val part = ShelfEntry(id = "y", title = "y", words = 100, index = 25)
        val done = ShelfEntry(id = "z", title = "z", words = 100, index = 99)

        assertEquals(0f, unread.progress, 0.001f)
        assertFalse(unread.started)
        assertEquals(0.25f, part.progress, 0.001f)
        assertTrue(part.started)
        assertFalse(part.finished)
        assertTrue(done.finished)
    }

    @Test
    fun `a book with no word count yet reports no progress`() {
        val counting = ShelfEntry(id = "w", title = "w", words = 0, index = 0)
        assertEquals(0f, counting.progress, 0.001f)
        assertFalse(counting.finished)
    }
}
