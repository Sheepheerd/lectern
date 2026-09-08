package com.lectern.core

/**
 * The piece Lectern reads to someone who has never seen it work. It is a real
 * book on the shelf, not a tutorial mode: the same tokenizer, the same pacing,
 * the same stage. What it happens to be about is how to read it.
 */
const val SAMPLE_TITLE = "How to read this"

/** The opening lines, shown running on the shelf before any book is loaded. */
val DEMO_LINES = """
    Read this without moving your eyes. Each word arrives in the same place,
    so there is nothing to scan and nothing to track. The marked letter is
    where your eye rests. That is the whole idea.
""".trimIndent()

fun sampleBook(): List<Section> = listOf(
    Section(
        title = "Where your eyes go",
        text = """
            $DEMO_LINES

            Reading a page normally means moving. Your eyes jump along the line, overshoot, come back, and find the start of the next line. Most of that motion is not reading. It is aiming.

            Lectern takes the aiming away. It holds one place on the screen and sends the words through it, lined up so the letter your eye wants is always in the same column. The two rails and the tick mark that column. After a few sentences you stop noticing them.
        """.trimIndent(),
    ),
    Section(
        title = "Speed is not the point",
        text = """
            Any reader can flash words at nine hundred a minute and take in nothing at all. What matters is the fastest pace at which you still follow the sentence, and that pace is yours, not a number from an advertisement.

            So the pacing pauses where writing pauses. A full stop holds nearly three times as long as an ordinary word. Commas breathe. Paragraph breaks breathe longer. Long words and numbers get more time, because they need it, and "Mr." or "e.g." does not fool it into stopping.

            Start at three hundred words a minute. Raise it by a step whenever the last page felt easy, and drop it the moment you notice yourself re-reading.
        """.trimIndent(),
    ),
    Section(
        title = "Working the controls",
        text = """
            Tap the word to start and stop. Swipe sideways to step back or forward a sentence, and up or down to change speed without leaving the page. Hold a finger on the words to see the paragraph you are standing in.

            The bookmark keeps a place worth returning to. Chapters jumps anywhere in the book. Settings holds the rest: how long each pause lasts, how many words arrive at once, whether the sentence you are inside sits dimmed beneath the word, which typeface, which palette.

            When you are ready for your own book, open a PDF, an EPUB, a document, or paste a link to an article. It is parsed on this phone. Nothing is uploaded, and there is nothing to sign into.
        """.trimIndent(),
    ),
)
