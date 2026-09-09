package com.lectern.core

/**
 * Turns extracted sections into display tokens plus chapter and paragraph
 * indexes.
 *
 * [Token.weight] is a multiplier on the base per-word duration (60000 / wpm ms)
 * for intrinsic difficulty (long or numeric words). Punctuation pauses are NOT
 * baked in here — [ReaderEngine] adds them at play time, so the reader can tune
 * how long a "." holds.
 */

enum class Punct { NONE, CLAUSE, SENTENCE }

data class Token(
    val text: String,
    val orp: Int,
    val weight: Float,
    val punct: Punct,
    val paraEnd: Boolean,
    val sentenceStart: Boolean,
    val para: Int,
)

data class Chapter(val title: String, val start: Int)

data class Paragraph(val start: Int, val count: Int)

data class Book(
    val tokens: List<Token>,
    val chapters: List<Chapter>,
    val paragraphs: List<Paragraph>,
)

private val SENTENCE_END = Regex("""[.!?…]["')\]»”’]*${'$'}""")
private val HARD_END = Regex("""[!?…]["')\]»”’]*${'$'}""")
private val CLAUSE_END = Regex("""[,;:—–]["')\]»”’]*${'$'}""")
private val TRAILING_CLOSERS = Regex("""["')\]»”’]+${'$'}""")
private val LEADING_OPENERS = Regex("""^["'(\[«“‘]+""")
private val SINGLE_CAPITAL = Regex("""^[A-Z]${'$'}""")
private val DOTTED_INITIALISM = Regex("""^([A-Za-z]\.)+[A-Za-z]?${'$'}""")
private val OPENS_SENTENCE = Regex("""^["'(\[«“‘]*[A-Z0-9]""")
private const val TRAILING_PUNCT_CHARS = "\"'()[]«»“”‘’.,;:!?…"

/**
 * Every character any of the patterns above can end on. A word not ending in
 * one of these cannot close a sentence or a clause, which spares the regexes
 * on the great majority of words — and there are a lot of words in a book.
 */
private const val PUNCT_TAIL = ".!?…,;:—–\"')]»”’"

private fun endsInPunctuation(word: String): Boolean =
    word.isNotEmpty() && word[word.length - 1] in PUNCT_TAIL

/**
 * Words whose trailing "." is an abbreviation, not a full stop. Titles are
 * never sentence ends ("Mr. Smith"); the ambiguous ones ("etc.", "Jr.") end a
 * sentence only when the next word starts a capitalized one.
 */
private val TITLE_ABBREVS = setOf(
    "mr", "mrs", "ms", "mx", "dr", "prof", "rev", "hon", "fr", "st", "mt",
    "gen", "col", "maj", "lt", "sgt", "capt", "cmdr", "gov", "sen", "rep",
)

private val OTHER_ABBREVS = setOf(
    "sr", "jr", "etc", "vs", "cf", "ca", "approx", "no", "fig", "figs",
    "vol", "vols", "pp", "ed", "eds", "al", "inc", "ltd", "co", "corp",
    "dept", "est", "ave", "blvd", "rd",
    "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
)

/** Does this word actually end a sentence? [nextWord] is null at paragraph end. */
internal fun endsSentence(word: String, nextWord: String?): Boolean {
    if (!endsInPunctuation(word)) return false
    if (HARD_END.containsMatchIn(word)) return true
    if (!SENTENCE_END.containsMatchIn(word)) return false
    // Strip surrounding quotes/brackets, then look at the final period.
    val core = word.replace(TRAILING_CLOSERS, "").replace(LEADING_OPENERS, "")
    if (!core.endsWith(".")) return true // period was inside the quotes
    val base = core.dropLast(1)
    if (SINGLE_CAPITAL.matches(base)) return false // initial: "J. R. R. Tolkien"
    val dotted = DOTTED_INITIALISM.matches(core) // "U.S.", "e.g.", "a.m."
    val lower = base.lowercase()
    if (lower in TITLE_ABBREVS) return false
    if (dotted || lower in OTHER_ABBREVS) {
        // Sentence over only if something capitalized (or nothing) follows.
        return nextWord == null || OPENS_SENTENCE.containsMatchIn(nextWord)
    }
    return true
}

/**
 * Optimal Recognition Point: the letter the eye fixates on. Slightly left of
 * center, per Spritz-style RSVP conventions.
 */
internal fun orpIndex(word: String): Int {
    var stripped = word.length
    while (stripped > 0 && word[stripped - 1] in TRAILING_PUNCT_CHARS) stripped--
    val len = if (stripped == 0) word.length else stripped
    return when {
        len <= 1 -> 0
        len <= 5 -> 1
        len <= 9 -> 2
        len <= 13 -> 3
        else -> 4
    }
}

private fun baseWeight(word: String): Float {
    var w = 1f
    if (word.length >= 9) w += 0.3f
    if (word.length >= 13) w += 0.3f
    if (word.any { it in '0'..'9' }) w += 0.5f
    return w
}

private val PARAGRAPH_SPLIT = Regex("""\n\s*\n+""")
private val WHITESPACE_RUN = Regex("""\s+""")

fun tokenize(sections: List<Section>): Book {
    val tokens = ArrayList<Token>()
    val chapters = ArrayList<Chapter>()
    val paragraphs = ArrayList<Paragraph>()
    var sentenceStart = true

    for ((si, section) in sections.withIndex()) {
        val paras = section.text
            .replace(Regex("""\r\n?"""), "\n")
            .split(PARAGRAPH_SPLIT)
            .map { it.replace(WHITESPACE_RUN, " ").trim() }
            .filter { it.isNotEmpty() }
        if (paras.isEmpty()) continue

        chapters += Chapter(
            title = section.title.ifBlank { "Section ${si + 1}" },
            start = tokens.size,
        )

        for (para in paras) {
            val words = para.split(" ").filter { it.isNotEmpty() }
            val paraIndex = paragraphs.size
            paragraphs += Paragraph(start = tokens.size, count = words.size)
            words.forEachIndexed { i, word ->
                val isParaEnd = i == words.lastIndex
                val punctuated = endsInPunctuation(word)
                val sentenceEnd = punctuated && endsSentence(word, words.getOrNull(i + 1))
                tokens += Token(
                    text = word,
                    orp = orpIndex(word),
                    weight = baseWeight(word),
                    punct = when {
                        sentenceEnd -> Punct.SENTENCE
                        punctuated && CLAUSE_END.containsMatchIn(word) -> Punct.CLAUSE
                        else -> Punct.NONE
                    },
                    paraEnd = isParaEnd,
                    sentenceStart = sentenceStart,
                    para = paraIndex,
                )
                sentenceStart = sentenceEnd || isParaEnd
            }
        }
    }
    return Book(tokens, chapters, paragraphs)
}

/**
 * The words shown in one flash. A chunk is one word by default; at a larger
 * [chunkSize] it gathers neighbours, but never across a sentence or paragraph
 * end, and never past a comfortable width — a chunk you have to shrink the type
 * to fit defeats the point.
 */
data class Chunk(val text: String, val orp: Int, val start: Int, val size: Int)

private const val MAX_CHUNK_CHARS = 20

fun chunkAt(tokens: List<Token>, index: Int, chunkSize: Int): Chunk? {
    val first = tokens.getOrNull(index) ?: return null
    if (chunkSize <= 1) return Chunk(first.text, first.orp, index, 1)

    val words = ArrayList<Token>(chunkSize)
    var width = 0
    for (i in index until minOf(index + chunkSize, tokens.size)) {
        val token = tokens[i]
        val added = token.text.length + if (words.isEmpty()) 0 else 1
        if (words.isNotEmpty() && width + added > MAX_CHUNK_CHARS) break
        words += token
        width += added
        // A full stop or a paragraph break closes the chunk: the pause that
        // follows belongs after these words, not in the middle of the next.
        if (token.punct == Punct.SENTENCE || token.paraEnd) break
    }

    val text = words.joinToString(" ") { it.text }
    // Pivot on the middle word, so the eye still lands inside the phrase.
    val pivotWord = words.size / 2
    var offset = 0
    for (i in 0 until pivotWord) offset += words[i].text.length + 1
    val orp = offset + words[pivotWord].orp
    return Chunk(text, orp, index, words.size)
}

/** Index of the first word of the sentence containing `tokens[i]`. */
fun sentenceStartBefore(tokens: List<Token>, i: Int): Int {
    for (j in minOf(i, tokens.lastIndex) downTo 1) {
        if (tokens[j].sentenceStart) return j
    }
    return 0
}

fun sentenceStartAfter(tokens: List<Token>, i: Int): Int {
    for (j in (i + 1)..tokens.lastIndex) {
        if (tokens[j].sentenceStart) return j
    }
    return tokens.lastIndex.coerceAtLeast(0)
}
