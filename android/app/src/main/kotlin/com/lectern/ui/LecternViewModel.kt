package com.lectern.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lectern.core.Chapter
import com.lectern.core.Document
import com.lectern.core.Paragraph
import com.lectern.core.ReaderEngine
import com.lectern.core.UnreadableDocument
import com.lectern.core.displayName
import com.lectern.core.extractDocument
import com.lectern.core.fetchArticle
import com.lectern.core.sectionsFromText
import com.lectern.core.titleFromFileName
import com.lectern.core.tokenize
import com.lectern.data.AppSettings
import com.lectern.data.Bookmark
import com.lectern.data.Library
import com.lectern.data.Settings
import com.lectern.data.ShelfEntry
import com.lectern.updateBookShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException

class LecternViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)
    private val settingsStore = Settings(app)

    val engine = ReaderEngine(viewModelScope)
    val shelf: StateFlow<List<ShelfEntry>> = library.shelf
    val settings: StateFlow<AppSettings> = settingsStore.state

    /** The book on screen, if the reader is open. */
    var current by mutableStateOf<ShelfEntry?>(null)
        private set
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
        private set
    var paragraphs by mutableStateOf<List<Paragraph>>(emptyList())
        private set
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set

    /** Set while a file is being parsed or an article fetched. */
    var importing by mutableStateOf(false)
        private set

    /**
     * A book the reader should be sent to: a fresh import, or one asked for by
     * a launcher shortcut. Held as state rather than an event so it survives
     * the wait between a cold start and the first composition.
     */
    private val _pendingOpen = MutableStateFlow<String?>(null)
    val pendingOpen: StateFlow<String?> = _pendingOpen.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        applySettings(settingsStore.current)
        refreshShortcuts()
    }

    private fun applySettings(s: AppSettings) {
        engine.setWpm(current?.wpm?.takeIf { s.perBookSpeed } ?: s.wpm)
        engine.setSentencePause(s.sentencePause)
        engine.clausePause = s.clausePause
        engine.paragraphPause = s.paragraphPause
        engine.rewindWords = s.rewindWords
        engine.chunkSize = s.chunkSize
        engine.warmUpWords = s.warmUpWords
        engine.breakMinutes = s.breakMinutes
        engine.stopAtChapterEnd = s.stopAtChapterEnd
    }

    private fun refreshShortcuts() {
        updateBookShortcuts(getApplication(), library.shelf.value)
    }

    // ---------- importing ----------

    fun importDocument(uri: Uri) {
        if (importing) return
        viewModelScope.launch {
            importing = true
            val app = getApplication<Application>()
            val name = runCatching { displayName(app.contentResolver, uri) }.getOrDefault("Untitled")
            try {
                val document = extractDocument(app.contentResolver, uri, app.cacheDir)
                openNew(document.title ?: titleFromFileName(name), document, source = name)
            } catch (e: Exception) {
                reportImportFailure(name, e)
            } finally {
                importing = false
            }
        }
    }

    /** Reads an article off the web, which is the app's only use of the network. */
    fun importUrl(url: String) {
        if (importing || url.isBlank()) return
        viewModelScope.launch {
            importing = true
            try {
                val document = fetchArticle(url)
                openNew(document.title ?: url.trim(), document, source = url.trim())
            } catch (e: Exception) {
                reportImportFailure(url.trim(), e)
            } finally {
                importing = false
            }
        }
    }

    fun importPastedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val title = trimmed.split(Regex("""\s+""")).take(6).joinToString(" ") + "…"
        openNew(title, Document(sectionsFromText(trimmed)), source = "Pasted text")
    }

    private fun reportImportFailure(what: String, e: Exception) {
        val reason = when (e) {
            is UnreadableDocument -> e.message
            is SecurityException -> "Lectern doesn't have permission to read it"
            is UnknownHostException -> "couldn't reach that address"
            else -> e.message ?: e.javaClass.simpleName
        }
        _messages.tryEmit("Couldn't read $what: $reason")
    }

    private fun openNew(title: String, document: Document, source: String) {
        val entry = library.add(title, document.sections, source, document.cover)
        refreshShortcuts()
        requestOpen(entry.id)
    }

    /** Ask the navigation host to show a book, if it is still on the shelf. */
    fun requestOpen(bookId: String) {
        if (library.entry(bookId) != null) _pendingOpen.value = bookId
    }

    fun openRequestHandled() {
        _pendingOpen.value = null
    }

    // ---------- reading ----------

    /** Loads [bookId] into the engine. Returns false if its text is gone. */
    fun open(bookId: String): Boolean {
        if (current?.id == bookId && engine.tokens.isNotEmpty()) return true
        val entry = library.entry(bookId) ?: return false
        val sections = library.sections(entry)
        if (sections == null) {
            _messages.tryEmit("\"${entry.title}\" is missing its text. Load the file again.")
            return false
        }
        rememberPosition()
        val book = tokenize(sections)
        chapters = book.chapters
        paragraphs = book.paragraphs
        bookmarks = library.bookmarks(bookId)
        engine.chapterStarts = book.chapters.map { it.start }
        engine.load(book.tokens, entry.index)
        current = entry
        // A book can keep its own pace.
        val settingsNow = settingsStore.current
        engine.setWpm(entry.wpm?.takeIf { settingsNow.perBookSpeed } ?: settingsNow.wpm)
        library.touch(entry.id)
        refreshShortcuts()
        return true
    }

    fun closeBook() {
        engine.pause()
        rememberPosition()
        current = null
        chapters = emptyList()
        paragraphs = emptyList()
        bookmarks = emptyList()
        engine.chapterStarts = emptyList()
    }

    fun rememberPosition() {
        val entry = current ?: return
        if (engine.tokens.isEmpty()) return
        library.rememberPosition(entry.id, engine.index, engine.tokens.size)
    }

    // ---------- bookmarks ----------

    fun addBookmark(note: String = "") {
        val entry = current ?: return
        val snippet = engine.tokens
            .drop(engine.index)
            .take(8)
            .joinToString(" ") { it.text }
        bookmarks = library.addBookmark(
            entry.id,
            Bookmark(
                index = engine.index,
                snippet = snippet,
                note = note.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun removeBookmark(bookmark: Bookmark) {
        val entry = current ?: return
        bookmarks = library.removeBookmark(entry.id, bookmark.index)
    }

    // ---------- shelf ----------

    fun entry(id: String?): ShelfEntry? = library.entry(id)

    fun continueReading(): ShelfEntry? = library.continueReading()

    fun tags(): List<String> = library.tags()

    fun coverPath(entry: ShelfEntry): String? = library.coverPath(entry)

    fun rename(entry: ShelfEntry, title: String) {
        library.rename(entry.id, title)
        if (current?.id == entry.id) current = library.entry(entry.id)
        refreshShortcuts()
    }

    fun setTags(entry: ShelfEntry, tags: List<String>) = library.setTags(entry.id, tags)

    fun setArchived(entry: ShelfEntry, archived: Boolean) {
        library.setArchived(entry.id, archived)
        refreshShortcuts()
    }

    fun restart(entry: ShelfEntry) {
        library.restart(entry.id)
        if (current?.id == entry.id) engine.seek(0)
    }

    fun remove(entry: ShelfEntry) {
        if (current?.id == entry.id) closeBook()
        library.remove(entry)
        refreshShortcuts()
    }

    // ---------- backup ----------

    fun exportShelf(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val books = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { library.exportTo(it) }
                }
            }.getOrNull()
            _messages.tryEmit(
                if (books == null) "Couldn't write the backup"
                else "Backed up $books ${if (books == 1) "book" else "books"}"
            )
        }
    }

    fun importShelf(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val books = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { library.importFrom(it) }
                }
            }.getOrNull()
            if (books == null) {
                _messages.tryEmit("That didn't look like a Lectern backup")
            } else {
                closeBook()
                refreshShortcuts()
                _messages.tryEmit("Restored $books ${if (books == 1) "book" else "books"}")
            }
        }
    }

    // ---------- settings ----------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsStore.update(transform)
        applySettings(settingsStore.current)
    }

    /** Speed changes from the reader stick — to this book, or to everything. */
    fun nudgeWpm(delta: Int) {
        val wpm = engine.nudgeWpm(delta)
        val entry = current
        if (settingsStore.current.perBookSpeed && entry != null) {
            library.rememberSpeed(entry.id, wpm)
        } else {
            settingsStore.update { it.copy(wpm = wpm) }
        }
    }

    fun setSentencePause(total: Float) {
        val applied = engine.setSentencePause(total)
        settingsStore.update { it.copy(sentencePause = applied) }
    }

    /** Index of the chapter the reader is currently inside. */
    fun currentChapterIndex(): Int {
        var ci = 0
        for ((i, chapter) in chapters.withIndex()) {
            if (chapter.start <= engine.index) ci = i else break
        }
        return ci
    }
}
