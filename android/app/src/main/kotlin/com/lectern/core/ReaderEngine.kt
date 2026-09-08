package com.lectern.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Why playback stopped on its own, so the reader can say so. */
enum class StopReason { None, End, Break, ChapterEnd }

/**
 * RSVP scheduler: advances [index] through [tokens], holding each flash for
 * 60000/wpm ms scaled by the words' weights plus punctuation pauses.
 *
 * The loop keeps an absolute target time so a slow frame doesn't accumulate
 * drift across a chapter.
 */
class ReaderEngine(private val scope: CoroutineScope) {

    var tokens by mutableStateOf<List<Token>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var wpm by mutableIntStateOf(300)
        private set
    var playing by mutableStateOf(false)
        private set
    /** Why the engine last stopped by itself. */
    var stoppedBecause by mutableStateOf(StopReason.None)
        private set

    val finished: Boolean get() = stoppedBecause == StopReason.End

    /** Words to step back when playback resumes. */
    var rewindWords: Int = 0

    /** Words shown at once (1–3). */
    var chunkSize: Int = 1
        set(value) {
            field = value.coerceIn(1, MAX_CHUNK)
        }

    /** Words spent climbing to full speed after a resume; 0 is off. */
    var warmUpWords: Int = 0

    /** Minutes of reading before an automatic break; 0 is off. */
    var breakMinutes: Int = 0

    /** Stop when a chapter ends rather than reading straight on. */
    var stopAtChapterEnd: Boolean = false

    /** Word indexes each chapter starts at, for [stopAtChapterEnd]. */
    var chapterStarts: List<Int> = emptyList()

    /** Extra multiples of the base word time added at punctuation. */
    private var sentenceExtra by mutableFloatStateOf(1.7f)
    var clausePause: Float = 0.6f
    var paragraphPause: Float = 1.2f

    /** How many base word-times a sentence-ending word occupies (>= 1). */
    val sentencePause: Float get() = sentenceExtra + 1f

    private var job: Job? = null

    /** Where the current run of reading started, for warm-up and breaks. */
    private var runStartIndex = 0
    private var runStartedAt = 0L

    val currentToken: Token? get() = tokens.getOrNull(index)

    /** The words on screen right now: one, or a chunk of up to three. */
    val currentChunk: Chunk? get() = chunkAt(tokens, index, chunkSize)

    fun load(tokens: List<Token>, startIndex: Int = 0) {
        pause()
        this.tokens = tokens
        index = startIndex.coerceIn(0, (tokens.size - 1).coerceAtLeast(0))
        stoppedBecause = StopReason.None
    }

    fun setWpm(value: Int): Int {
        wpm = value.coerceIn(MIN_WPM, MAX_WPM)
        return wpm
    }

    fun setSentencePause(total: Float): Float {
        sentenceExtra = total.coerceIn(1f, 5f) - 1f
        return sentencePause
    }

    /** How long a chunk holds, in ms, before warm-up is applied. */
    fun delayFor(chunk: Chunk): Double {
        var m = 0.0
        for (i in chunk.start until chunk.start + chunk.size) {
            val t = tokens.getOrNull(i) ?: continue
            m += t.weight
        }
        val last = tokens.getOrNull(chunk.start + chunk.size - 1)
        if (last != null) {
            when (last.punct) {
                Punct.SENTENCE -> m += sentenceExtra
                Punct.CLAUSE -> m += clausePause
                Punct.NONE -> Unit
            }
            if (last.paraEnd) m += paragraphPause
        }
        return (60_000.0 / wpm) * m
    }

    /** How long the single word at [i] would hold, for estimates and tests. */
    fun delayForWordAt(i: Int): Double {
        val token = tokens.getOrNull(i) ?: return 0.0
        return delayFor(Chunk(token.text, token.orp, i, 1))
    }

    /**
     * Speed multiplier while warming up: reading opens at 70% of the dial and
     * reaches full speed [warmUpWords] words later, which gives the eye a
     * moment to settle without the reader having to do anything.
     */
    fun warmUpFactor(at: Int = index): Float {
        if (warmUpWords <= 0) return 1f
        val read = (at - runStartIndex).coerceAtLeast(0)
        if (read >= warmUpWords) return 1f
        return WARM_UP_FLOOR + (1f - WARM_UP_FLOOR) * (read.toFloat() / warmUpWords)
    }

    fun play() {
        if (playing || tokens.isEmpty()) return
        if (index >= tokens.lastIndex && tokens.size > 1) {
            index = 0 // finished: replay
        } else if (index > 0) {
            index = (index - rewindWords).coerceAtLeast(0)
        }
        stoppedBecause = StopReason.None
        runStartIndex = index
        runStartedAt = System.currentTimeMillis()
        playing = true
        startLoop()
    }

    fun pause() {
        job?.cancel()
        job = null
        playing = false
    }

    fun toggle() = if (playing) pause() else play()

    fun seek(target: Int) {
        index = target.coerceIn(0, tokens.lastIndex.coerceAtLeast(0))
        stoppedBecause = StopReason.None
        if (playing) {
            runStartIndex = index
            job?.cancel()
            startLoop()
        }
    }

    fun nudgeWpm(delta: Int) = setWpm(wpm + delta)

    /** How far through the book the current word is, 0f..1f. */
    val progress: Float
        get() = if (tokens.size > 1) index.toFloat() / (tokens.size - 1) else 0f

    fun previousSentence() = seek(sentenceStartBefore(tokens, index - 1))

    fun nextSentence() = seek(sentenceStartAfter(tokens, index))

    /** Estimated ms remaining from the current position at current settings. */
    fun remainingMs(): Double {
        var sum = 0.0
        var i = index
        while (i <= tokens.lastIndex) {
            val chunk = chunkAt(tokens, i, chunkSize) ?: break
            sum += delayFor(chunk)
            i += chunk.size
        }
        return sum
    }

    private fun startLoop() {
        job = scope.launch {
            var targetNanos = System.nanoTime()
            while (isActive) {
                val chunk = chunkAt(tokens, index, chunkSize) ?: break
                val next = index + chunk.size
                if (next > tokens.lastIndex) {
                    stop(StopReason.End)
                    break
                }
                if (breakMinutes > 0 &&
                    System.currentTimeMillis() - runStartedAt >= breakMinutes * 60_000L
                ) {
                    stop(StopReason.Break)
                    break
                }
                if (stopAtChapterEnd && next != runStartIndex && next in chapterStarts) {
                    index = next
                    stop(StopReason.ChapterEnd)
                    break
                }

                targetNanos += (delayFor(chunk) / warmUpFactor() * 1_000_000L).toLong()
                val waitMs = (targetNanos - System.nanoTime()) / 1_000_000L
                if (waitMs > 0) delay(waitMs) else targetNanos = System.nanoTime()
                index = next
            }
        }
    }

    private fun stop(reason: StopReason) {
        playing = false
        stoppedBecause = reason
    }

    companion object {
        const val MIN_WPM = 60
        const val MAX_WPM = 1200
        const val WPM_STEP = 25
        const val MAX_CHUNK = 3
        private const val WARM_UP_FLOOR = 0.7f
    }
}
