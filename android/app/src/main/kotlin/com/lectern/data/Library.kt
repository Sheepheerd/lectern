package com.lectern.data

import android.content.Context
import androidx.compose.runtime.Immutable
import com.lectern.core.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The shelf: a small index of books kept in a JSON file, with each book's text
 * stored beside it under books/<id>.json. Reading positions live in the index,
 * so resuming is a single small read.
 */
@Immutable
@Serializable
data class ShelfEntry(
    val id: String,
    val title: String,
    val words: Int = 0,
    val index: Int = 0,
    val addedAt: Long = 0L,
    val openedAt: Long = 0L,
    /** Where the file came from, shown in the book's details. */
    val source: String = "",
    /** Reading speed for this book, when speeds are kept per book. */
    val wpm: Int? = null,
    val tags: List<String> = emptyList(),
    val archived: Boolean = false,
    /** File name of the cover under covers/, if the format had one. */
    val cover: String? = null,
) {
    val progress: Float get() = if (words > 0) (index.toFloat() / words).coerceIn(0f, 1f) else 0f
    val started: Boolean get() = index > 0
    val finished: Boolean get() = words > 0 && index >= words - 1
}

class Library(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val root = context.filesDir
    private val shelfFile = File(root, "shelf.json")
    private val booksDir = File(root, "books").apply { mkdirs() }
    private val coversDir = File(root, "covers").apply { mkdirs() }
    private val bookmarksDir = File(root, "bookmarks").apply { mkdirs() }

    private val _shelf = MutableStateFlow(readShelf())
    val shelf: StateFlow<List<ShelfEntry>> = _shelf.asStateFlow()

    private fun readShelf(): List<ShelfEntry> = runCatching {
        if (!shelfFile.exists()) emptyList()
        else json.decodeFromString<List<ShelfEntry>>(shelfFile.readText())
    }.getOrDefault(emptyList())

    private fun write(entries: List<ShelfEntry>) {
        runCatching { shelfFile.writeText(json.encodeToString(entries)) }
        _shelf.value = entries
    }

    private fun mutate(id: String, transform: (ShelfEntry) -> ShelfEntry) {
        val entries = _shelf.value
        if (entries.none { it.id == id }) return
        write(entries.map { if (it.id == id) transform(it) else it })
    }

    fun entry(id: String?): ShelfEntry? = _shelf.value.firstOrNull { it.id == id }

    /** Every tag in use, for the shelf's filter row. */
    fun tags(): List<String> = _shelf.value.flatMap { it.tags }.distinct().sorted()

    /**
     * The book to offer as "continue reading": the one most recently opened
     * that is under way, not finished and not archived.
     */
    fun continueReading(): ShelfEntry? = _shelf.value
        .filter { it.openedAt > 0 && it.started && !it.finished && !it.archived }
        .maxByOrNull { it.openedAt }

    fun add(
        title: String,
        sections: List<Section>,
        source: String = "",
        cover: ByteArray? = null,
    ): ShelfEntry {
        val now = System.currentTimeMillis()
        val id = "b" + now.toString(36)
        val coverName = cover?.let {
            val name = "$id.jpg"
            runCatching { File(coversDir, name).writeBytes(it) }.getOrNull()?.let { name }
        }
        val entry = ShelfEntry(
            id = id,
            title = title,
            addedAt = now,
            openedAt = now,
            source = source,
            cover = coverName,
        )
        bookFile(id).writeText(json.encodeToString(sections))
        write(listOf(entry) + _shelf.value)
        return entry
    }

    fun sections(entry: ShelfEntry): List<Section>? = runCatching {
        val file = bookFile(entry.id)
        if (file.exists()) json.decodeFromString<List<Section>>(file.readText()) else null
    }.getOrNull()

    fun coverPath(entry: ShelfEntry): String? = entry.cover
        ?.let { File(coversDir, it) }
        ?.takeIf { it.exists() }
        ?.absolutePath

    fun rememberPosition(id: String, index: Int, words: Int) =
        mutate(id) { it.copy(index = index, words = words) }

    fun rememberSpeed(id: String, wpm: Int) = mutate(id) { it.copy(wpm = wpm) }

    fun touch(id: String) = mutate(id) { it.copy(openedAt = System.currentTimeMillis()) }

    fun rename(id: String, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) mutate(id) { it.copy(title = clean) }
    }

    fun setTags(id: String, tags: List<String>) = mutate(id) {
        it.copy(tags = tags.map(String::trim).filter(String::isNotEmpty).distinct())
    }

    fun setArchived(id: String, archived: Boolean) = mutate(id) { it.copy(archived = archived) }

    /** Back to the first word, keeping the book on the shelf. */
    fun restart(id: String) = mutate(id) { it.copy(index = 0) }

    fun remove(entry: ShelfEntry) {
        bookFile(entry.id).delete()
        entry.cover?.let { File(coversDir, it).delete() }
        bookmarksFile(entry.id).delete()
        write(_shelf.value.filterNot { it.id == entry.id })
    }

    // ---------- bookmarks ----------

    fun bookmarks(bookId: String): List<Bookmark> = runCatching {
        val file = bookmarksFile(bookId)
        if (file.exists()) json.decodeFromString<List<Bookmark>>(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    fun addBookmark(bookId: String, bookmark: Bookmark): List<Bookmark> {
        val next = (bookmarks(bookId) + bookmark).sortedBy { it.index }
        writeBookmarks(bookId, next)
        return next
    }

    fun removeBookmark(bookId: String, index: Int): List<Bookmark> {
        val next = bookmarks(bookId).filterNot { it.index == index }
        writeBookmarks(bookId, next)
        return next
    }

    private fun writeBookmarks(bookId: String, marks: List<Bookmark>) {
        runCatching { bookmarksFile(bookId).writeText(json.encodeToString(marks)) }
    }

    // ---------- backup ----------

    /** Writes the whole shelf — index, texts, covers, bookmarks — as a zip. */
    fun exportTo(out: OutputStream): Int {
        var books = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(SHELF_ENTRY))
            zip.write(json.encodeToString(_shelf.value).toByteArray())
            zip.closeEntry()
            for (entry in _shelf.value) {
                copyInto(zip, "books/${entry.id}.json", bookFile(entry.id))
                entry.cover?.let { copyInto(zip, "covers/$it", File(coversDir, it)) }
                copyInto(zip, "bookmarks/${entry.id}.json", bookmarksFile(entry.id))
                books++
            }
        }
        return books
    }

    private fun copyInto(zip: ZipOutputStream, name: String, file: File) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Restores a zip written by [exportTo], replacing everything on the shelf.
     * Returns how many books were read, or null if the file wasn't a backup.
     */
    fun importFrom(input: InputStream): Int? {
        val staging = File(root, "restore").apply { deleteRecursively(); mkdirs() }
        var shelfJson: String? = null
        try {
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.isSafeEntryName()) {
                        if (name == SHELF_ENTRY) {
                            shelfJson = zip.readBytes().decodeToString()
                        } else {
                            val target = File(staging, name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val entries = shelfJson?.let {
                runCatching { json.decodeFromString<List<ShelfEntry>>(it) }.getOrNull()
            } ?: return null

            booksDir.deleteRecursively(); booksDir.mkdirs()
            coversDir.deleteRecursively(); coversDir.mkdirs()
            bookmarksDir.deleteRecursively(); bookmarksDir.mkdirs()
            File(staging, "books").listFiles()?.forEach { it.copyTo(File(booksDir, it.name), true) }
            File(staging, "covers").listFiles()?.forEach { it.copyTo(File(coversDir, it.name), true) }
            File(staging, "bookmarks").listFiles()?.forEach {
                it.copyTo(File(bookmarksDir, it.name), true)
            }
            // Keep only books whose text actually made it across.
            val kept = entries.filter { bookFile(it.id).exists() }
            write(kept)
            return kept.size
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun bookFile(id: String) = File(booksDir, "$id.json")

    private fun bookmarksFile(id: String) = File(bookmarksDir, "$id.json")

    private companion object {
        const val SHELF_ENTRY = "shelf.json"
    }
}

/** A saved place in a book. */
@Immutable
@Serializable
data class Bookmark(
    val index: Int,
    /** A few words from the text, so the list reads like the book. */
    val snippet: String = "",
    val note: String = "",
    val createdAt: Long = 0L,
)

/** Rejects zip entries that would escape the directory they're unpacked into. */
private fun String.isSafeEntryName(): Boolean =
    !startsWith("/") && !contains("..") && !contains('\\')

/** The shelf in the order the reader asked for. */
fun List<ShelfEntry>.sortedBy(order: ShelfOrder): List<ShelfEntry> = when (order) {
    ShelfOrder.Recent -> sortedByDescending { maxOf(it.openedAt, it.addedAt) }
    ShelfOrder.Added -> sortedByDescending { it.addedAt }
    ShelfOrder.Title -> sortedBy { it.title.lowercase() }
    ShelfOrder.Progress -> sortedBy { it.progress }
}

/** What the shelf's filter row can narrow the books down to. */
sealed interface ShelfFilter {
    data object All : ShelfFilter
    data object Reading : ShelfFilter
    data object Finished : ShelfFilter
    data object Archived : ShelfFilter
    data class Tagged(val tag: String) : ShelfFilter
}

fun List<ShelfEntry>.filteredBy(filter: ShelfFilter): List<ShelfEntry> = when (filter) {
    ShelfFilter.All -> filterNot { it.archived }
    ShelfFilter.Reading -> filter { it.started && !it.finished && !it.archived }
    ShelfFilter.Finished -> filter { it.finished && !it.archived }
    ShelfFilter.Archived -> filter { it.archived }
    is ShelfFilter.Tagged -> filter { !it.archived && filter.tag in it.tags }
}
