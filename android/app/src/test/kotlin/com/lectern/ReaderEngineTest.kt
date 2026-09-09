package com.lectern

import com.lectern.core.Punct
import com.lectern.core.ReaderEngine
import com.lectern.core.StopReason
import com.lectern.core.Section
import com.lectern.core.tokenize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEngineTest {

    // Unconfined runs the playback loop eagerly up to its first delay, so the
    // engine's synchronous state is settled by the time play() returns.
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val engine = ReaderEngine(scope)

    private val tokens = tokenize(
        listOf(Section("", "One two three four five six seven eight nine ten. Eleven twelve."))
    ).tokens

    @After
    fun tearDown() {
        engine.pause()
        scope.cancel()
    }

    @Test
    fun `resuming steps back a few words`() {
        engine.load(tokens)
        engine.rewindWords = 2
        engine.seek(6)
        engine.play()
        assertEquals(4, engine.index)
        engine.pause()
    }

    @Test
    fun `rewind never runs off the front, and is off at zero`() {
        engine.load(tokens)
        engine.rewindWords = 5
        engine.seek(1)
        engine.play()
        assertEquals(0, engine.index)
        engine.pause()

        engine.rewindWords = 0
        engine.seek(3)
        engine.play()
        assertEquals(3, engine.index)
        engine.pause()
    }

    @Test
    fun `finishing and playing again starts from the top`() {
        engine.load(tokens)
        engine.rewindWords = 2
        engine.seek(tokens.lastIndex)
        engine.play()
        assertEquals(0, engine.index)
        engine.pause()
    }

    @Test
    fun `speed is clamped to the dial's range`() {
        assertEquals(ReaderEngine.MAX_WPM, engine.setWpm(5_000))
        assertEquals(ReaderEngine.MIN_WPM, engine.setWpm(1))
    }

    @Test
    fun `a sentence end holds for the pause the reader asked for`() {
        engine.load(tokens)
        engine.setWpm(300)
        engine.setSentencePause(3f)
        val plain = tokens.indexOfFirst { it.punct == Punct.NONE && !it.paraEnd }
        val ending = tokens.indexOfFirst { it.punct == Punct.SENTENCE && !it.paraEnd }
        // A full stop holds for its own word-time plus two more.
        assertEquals(engine.delayForWordAt(plain) * 3, engine.delayForWordAt(ending), 1.0)
    }

    @Test
    fun `warm-up opens slower and reaches full speed`() {
        engine.load(tokens)
        engine.warmUpWords = 10
        engine.rewindWords = 0
        engine.seek(0)
        engine.play()
        assertEquals(0.7f, engine.warmUpFactor(0), 0.001f)
        assertEquals(0.85f, engine.warmUpFactor(5), 0.001f)
        assertEquals(1f, engine.warmUpFactor(10), 0.001f)
        assertEquals(1f, engine.warmUpFactor(50), 0.001f)
        engine.pause()
    }

    @Test
    fun `no warm-up means full speed from the first word`() {
        engine.load(tokens)
        engine.warmUpWords = 0
        assertEquals(1f, engine.warmUpFactor(0), 0.001f)
    }

    @Test
    fun `a chunk holds as long as the words inside it`() {
        engine.load(tokens)
        engine.setWpm(300)
        engine.chunkSize = 3
        val chunk = engine.currentChunk!!
        val words = (0 until chunk.size).sumOf { engine.delayForWordAt(chunk.start + it) }
        assertEquals(words, engine.delayFor(chunk), 1.0)
    }

    @Test
    fun `chunking does not change how long the book takes`() {
        engine.load(tokens)
        engine.chunkSize = 1
        val single = engine.remainingMs()
        engine.chunkSize = 3
        assertEquals(single, engine.remainingMs(), 1.0)
    }

    @Test
    fun `the chunk size dial is clamped to what fits`() {
        engine.chunkSize = 99
        assertEquals(ReaderEngine.MAX_CHUNK, engine.chunkSize)
        engine.chunkSize = 0
        assertEquals(1, engine.chunkSize)
    }

    @Test
    fun `the pause dial refuses values outside one to five`() {
        assertEquals(1f, engine.setSentencePause(0.2f), 0.001f)
        assertEquals(5f, engine.setSentencePause(9f), 0.001f)
    }

    @Test
    fun `reading to the end reports why it stopped`() {
        engine.load(tokens)
        assertEquals(StopReason.None, engine.stoppedBecause)
        engine.seek(tokens.lastIndex)
        engine.play() // replays from the top rather than sitting at the end
        assertEquals(0, engine.index)
        assertEquals(StopReason.None, engine.stoppedBecause)
        engine.pause()
    }

    // ---------- stopping at chapter ends ----------

    /** Three chapters of five words each: starts at 0, 5 and 10. */
    private fun threeChapters(): ReaderEngine {
        val book = tokenize(
            listOf(
                Section("One", "Alpha bravo charlie delta echo"),
                Section("Two", "Foxtrot golf hotel india juliett"),
                Section("Three", "Kilo lima mike november oscar"),
            )
        )
        engine.load(book.tokens)
        engine.chapterStarts = book.chapters.map { it.start }
        engine.stopAtChapterEnd = true
        return engine
    }

    @Test
    fun `chapter starts are where expected`() {
        threeChapters()
        assertEquals(listOf(0, 5, 10), engine.chapterStarts)
    }

    @Test
    fun `reading into the next chapter stops there`() {
        threeChapters()
        engine.rewindWords = 0
        engine.seek(4) // last word of chapter one
        engine.play()
        assertEquals(StopReason.ChapterEnd, engine.stoppedBecause)
        assertEquals(5, engine.index)
        assertFalse(engine.playing)
    }

    /**
     * Jumping to a chapter and pressing play used to stop on the spot: the
     * rewind stepped back over the chapter start, so the first words read
     * crossed it again and looked like the end of a chapter.
     */
    @Test
    fun `starting at a chapter start does not stop on the spot`() {
        threeChapters()
        engine.rewindWords = 1
        engine.seek(5) // first word of chapter two, chosen from the menu
        engine.play()
        assertTrue("playback should carry on into the chapter", engine.playing)
        assertEquals(StopReason.None, engine.stoppedBecause)
        engine.pause()
    }

    /** The same trap on the way out of an automatic stop. */
    @Test
    fun `resuming after a chapter end carries on into the next chapter`() {
        threeChapters()
        engine.rewindWords = 0
        engine.seek(4)
        engine.play()
        assertEquals(StopReason.ChapterEnd, engine.stoppedBecause)

        engine.rewindWords = 1
        engine.play()
        assertTrue("resuming should not stop at the boundary again", engine.playing)
        assertEquals(StopReason.None, engine.stoppedBecause)
        engine.pause()
    }

    /** A boundary already behind the reader must not stop them a second time. */
    @Test
    fun `a chapter start just behind the reader does not stop playback`() {
        threeChapters()
        engine.rewindWords = 3
        engine.seek(6) // a word into chapter two, rewinding back into chapter one
        engine.play()
        assertEquals(3, engine.index)
        assertTrue(engine.playing)
        assertEquals(StopReason.None, engine.stoppedBecause)
        engine.pause()
    }

    @Test
    fun `progress runs from the first word to the last`() {
        engine.load(tokens)
        assertEquals(0f, engine.progress, 0.001f)
        engine.seek(tokens.lastIndex)
        assertEquals(1f, engine.progress, 0.001f)
        engine.seek(tokens.lastIndex / 2)
        assertTrue(engine.progress in 0.4f..0.6f)
    }
}
