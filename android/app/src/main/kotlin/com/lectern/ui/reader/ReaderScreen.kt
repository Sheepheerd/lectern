package com.lectern.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lectern.core.ReaderEngine
import com.lectern.core.StopReason
import com.lectern.core.Token
import com.lectern.core.sentenceStartAfter
import com.lectern.core.sentenceStartBefore
import com.lectern.data.Bookmark as SavedBookmark
import com.lectern.ui.LecternViewModel
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    vm: LecternViewModel,
    bookId: String,
    onBack: () -> Unit,
) {
    val engine = vm.engine
    val settings by vm.settings.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(ReaderSheet.None) }
    var hintShown by remember(bookId) { mutableStateOf(true) }
    var peeking by remember { mutableStateOf(false) }

    // The route carries the book id, so this is also what reopens the book
    // after the process is killed and the back stack restored.
    LaunchedEffect(bookId) {
        if (!vm.open(bookId)) onBack()
    }

    val title = if (vm.loading) "" else vm.current?.title.orEmpty()

    // The chrome recedes while reading; the word is the only bright thing.
    val chromeAlpha by animateFloatAsState(
        targetValue = if (engine.playing && settings.dimChrome) 0.4f else 1f,
        label = "chrome",
    )

    // Summing every remaining word is cheap, but not 20 times a second: the
    // estimate only needs refreshing every few words.
    val clockBucket = engine.index / 16
    val timeLeft = remember(clockBucket, engine.wpm, engine.sentencePause, engine.tokens) {
        timeLeft(engine)
    }

    // A killed process never sees onStop, so keep the saved place fresh.
    LaunchedEffect(engine.playing, bookId) {
        while (engine.playing) {
            delay(10_000)
            vm.rememberPosition()
        }
    }

    val view = LocalView.current
    LaunchedEffect(engine.playing, settings.keepScreenOn) {
        view.keepScreenOn = engine.playing && settings.keepScreenOn
        if (engine.playing) hintShown = false
    }

    // Said once, while the words are moving and the claim can be checked, then
    // never again on this device.
    var pivotHint by remember { mutableStateOf(false) }
    LaunchedEffect(engine.playing) {
        if (!engine.playing || settings.seenPivotHint) return@LaunchedEffect
        pivotHint = true
        delay(7_000)
        pivotHint = false
        vm.markPivotHintSeen()
    }

    // Full screen, if asked for: the status and navigation bars step aside
    // while a book is open, and come back when it closes.
    if (settings.fullScreen) {
        val window = (view.context as? Activity)?.window
        DisposableEffect(window) {
            val controller = window?.let { WindowInsetsControllerCompat(it, view) }
            controller?.apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    fun leave() {
        vm.closeBook()
        onBack()
    }

    BackHandler { leave() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.alpha(chromeAlpha),
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to shelf")
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontStyle = FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = timeLeft,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.addBookmark() }) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Bookmark this place")
                    }
                    if (vm.bookmarks.isNotEmpty()) {
                        IconButton(onClick = {
                            engine.pause()
                            sheet = ReaderSheet.Bookmarks
                        }) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarks")
                        }
                    }
                    if (vm.chapters.size > 1) {
                        IconButton(onClick = {
                            engine.pause()
                            sheet = ReaderSheet.Chapters
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Chapters")
                        }
                    }
                    IconButton(onClick = {
                        engine.pause()
                        sheet = ReaderSheet.Paragraph
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = "Current paragraph")
                    }
                },
            )
        },
    ) { padding ->
        val stage = @Composable { modifier: Modifier ->
            // A book not yet in memory takes a moment to read and tokenize.
            // Better a quiet turning circle than a stage with nothing on it.
            if (vm.loading) {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                WordStage(
                    chunk = engine.currentChunk,
                    modifier = modifier,
                    font = settings.font,
                    wordSize = settings.wordSize,
                    pivotStyle = settings.pivotStyle,
                    pivotShade = settings.pivotShade,
                    showRails = settings.showRails,
                    tapZones = settings.tapZones,
                    holdToPeek = settings.holdToPeek,
                    onTap = { tap ->
                        when (tap) {
                            StageTap.Toggle -> engine.toggle()
                            StageTap.Previous -> engine.previousSentence()
                            StageTap.Next -> engine.nextSentence()
                        }
                    },
                    onSpeed = { faster ->
                        vm.nudgeWpm(if (faster) ReaderEngine.WPM_STEP else -ReaderEngine.WPM_STEP)
                    },
                    onPeek = { peeking = it },
                )
            }
        }

        val belowStage = @Composable { modifier: Modifier ->
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (settings.contextLine) {
                    ContextLine(vm, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
                Hint(engine, hintShown, settings.tapZones, pivotHint)
                Scrubber(engine, Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(chromeAlpha)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Transport(engine)
                    SpeedRow(vm)
                    PauseRow(vm)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            // Wide and short — a landscape phone, or a tablet — puts the
            // controls beside the stage instead of under it.
            if (maxWidth > maxHeight) {
                Row(modifier = Modifier.fillMaxSize()) {
                    stage(Modifier.weight(1.4f).fillMaxHeight())
                    belowStage(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 16.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    stage(Modifier.fillMaxWidth().weight(1f))
                    belowStage(Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Holding a finger on the stage shows the paragraph you're inside.
    AnimatedVisibility(visible = peeking) {
        PeekOverlay(vm)
    }

    when (sheet) {
        ReaderSheet.None -> Unit

        ReaderSheet.Chapters -> ModalBottomSheet(
            onDismissRequest = { sheet = ReaderSheet.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ChapterList(vm) { start ->
                engine.seek(start)
                sheet = ReaderSheet.None
            }
        }

        ReaderSheet.Paragraph -> ModalBottomSheet(
            onDismissRequest = { sheet = ReaderSheet.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ParagraphSheet(vm) { target ->
                engine.seek(target)
                sheet = ReaderSheet.None
            }
        }

        ReaderSheet.Bookmarks -> ModalBottomSheet(
            onDismissRequest = { sheet = ReaderSheet.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            BookmarkList(
                vm = vm,
                onJump = { target ->
                    engine.seek(target)
                    sheet = ReaderSheet.None
                },
            )
        }
    }
}

private enum class ReaderSheet { None, Chapters, Paragraph, Bookmarks }

/** What the stage says when nothing is moving. */
@Composable
private fun Hint(
    engine: ReaderEngine,
    hintShown: Boolean,
    tapZones: Boolean,
    pivotHint: Boolean = false,
) {
    if (pivotHint && engine.playing) {
        Text(
            text = "The marked letter stays in one column. Rest your eyes there.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        return
    }
    val text = when (engine.stoppedBecause) {
        StopReason.End -> "End of the book. Tap to read it again."
        StopReason.Break -> "Break. Tap when you are ready."
        StopReason.ChapterEnd -> "End of the chapter. Tap to carry on."
        StopReason.None -> if (tapZones) {
            "Tap the middle to start. The sides step a sentence."
        } else {
            "Tap to start. Swipe sideways for sentences, up and down for speed."
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(if (hintShown || engine.stoppedBecause != StopReason.None) 1f else 0f),
    )
}

/**
 * The sentence you are inside, dimmed, with the current words lit. Peripheral
 * vision keeps the thread while the eye stays on the pivot.
 */
@Composable
private fun ContextLine(vm: LecternViewModel, modifier: Modifier = Modifier) {
    val engine = vm.engine
    val tokens = engine.tokens
    if (tokens.isEmpty()) return

    val start = remember(engine.index, tokens) { sentenceStartBefore(tokens, engine.index) }
    val end = remember(engine.index, tokens) {
        val after = sentenceStartAfter(tokens, engine.index)
        if (after <= start) tokens.size else after
    }
    val chunkSize = engine.currentChunk?.size ?: 1

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
    ) {
        for (i in start until end) {
            val token = tokens.getOrNull(i) ?: break
            val lit = i >= engine.index && i < engine.index + chunkSize
            Text(
                text = token.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (lit) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Scrubber(engine: ReaderEngine, modifier: Modifier = Modifier) {
    val last = max(1, engine.tokens.lastIndex)
    Slider(
        value = engine.index.toFloat(),
        onValueChange = { engine.seek(it.roundToInt()) },
        valueRange = 0f..last.toFloat(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Transport(engine: ReaderEngine) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconButton(onClick = engine::previousSentence) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous sentence")
        }
        FilledIconButton(
            onClick = engine::toggle,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = if (engine.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (engine.playing) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = engine::nextSentence) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next sentence")
        }
    }
}

@Composable
private fun SpeedRow(vm: LecternViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = { vm.nudgeWpm(-ReaderEngine.WPM_STEP) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Slower")
        }
        Text(
            text = "${vm.engine.wpm} wpm",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(120.dp),
        )
        IconButton(onClick = { vm.nudgeWpm(ReaderEngine.WPM_STEP) }) {
            Icon(Icons.Filled.Add, contentDescription = "Faster")
        }
    }
}

@Composable
private fun PauseRow(vm: LecternViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "pause at .",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = vm.engine.sentencePause,
            onValueChange = vm::setSentencePause,
            valueRange = 1f..5f,
            steps = 15,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = "×" + trimZeros(vm.engine.sentencePause),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun ChapterList(vm: LecternViewModel, onPick: (Int) -> Unit) {
    val currentIndex = vm.currentChapterIndex()
    val total = vm.engine.tokens.size
    val state = rememberLazyListState()
    LaunchedEffect(Unit) { state.scrollToItem(max(0, currentIndex - 2)) }

    LazyColumn(state = state, modifier = Modifier.navigationBarsPadding()) {
        item {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(vm.chapters) { i, chapter ->
            val selected = i == currentIndex
            val start = chapter.start
            val end = vm.chapters.getOrNull(i + 1)?.start ?: total
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(start) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chapterMeta(vm.engine, start, end, selected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected && end > start) {
                    val done = ((vm.engine.index - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { done },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkList(vm: LecternViewModel, onJump: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.addBookmark() }) { Text("Add here") }
        }
        LazyColumn {
            items(vm.bookmarks.size) { i ->
                val mark = vm.bookmarks[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onJump(mark.index) }
                        .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mark.snippet.ifBlank { "Word ${mark.index}" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = bookmarkMeta(vm, mark),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.removeBookmark(mark) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove bookmark",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The paragraph you're standing in; tap any word to jump straight to it. */
@Composable
private fun ParagraphSheet(vm: LecternViewModel, onPick: (Int) -> Unit) {
    val engine = vm.engine
    val para = engine.currentToken?.let { vm.paragraphs.getOrNull(it.para) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Current paragraph", style = MaterialTheme.typography.titleMedium)
        if (para == null) {
            Text("No book loaded.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        ParagraphText(
            tokens = engine.tokens,
            paragraphStart = para.start,
            paragraphCount = para.count,
            current = engine.index,
            currentSpan = SpanStyle(
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                background = MaterialTheme.colorScheme.primaryContainer,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            onPick = onPick,
        )
    }
}

/** The same paragraph, shown for as long as a finger stays on the stage. */
@Composable
private fun PeekOverlay(vm: LecternViewModel) {
    val engine = vm.engine
    val para = engine.currentToken?.let { vm.paragraphs.getOrNull(it.para) } ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ParagraphText(
            tokens = engine.tokens,
            paragraphStart = para.start,
            paragraphCount = para.count,
            current = engine.index,
            currentSpan = SpanStyle(color = MaterialTheme.colorScheme.primary),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A paragraph as one run of text rather than one composable per word. A page of
 * PDF prose can reflow into a paragraph thousands of words long; laying that out
 * as separate widgets janks the sheet and leaves it unscrollable. Here the words
 * are spans in a single string, taps map back to a word through the text layout,
 * and a window around the current word keeps even a runaway paragraph cheap.
 */
@Composable
private fun ParagraphText(
    tokens: List<Token>,
    paragraphStart: Int,
    paragraphCount: Int,
    current: Int,
    currentSpan: SpanStyle,
    color: Color,
    modifier: Modifier = Modifier,
    onPick: ((Int) -> Unit)? = null,
) {
    val window = remember(tokens, paragraphStart, paragraphCount, current, currentSpan) {
        buildParagraphWindow(tokens, paragraphStart, paragraphCount, current, currentSpan)
    }
    if (window.text.isEmpty()) return

    var layout by remember(window) { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()

    // Open on the word the reader is actually on, not at the top of a wall.
    LaunchedEffect(layout, window) {
        val result = layout ?: return@LaunchedEffect
        val offset = window.offsetOfCurrent ?: return@LaunchedEffect
        val top = runCatching { result.getBoundingBox(offset).top }.getOrNull() ?: return@LaunchedEffect
        scroll.animateScrollTo((top - 80f).toInt().coerceAtLeast(0))
    }

    Text(
        text = window.text,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        onTextLayout = { layout = it },
        // The scroller sits inside the tap handler, not the other way round: a
        // tap detector nearest the pointer swallows the drag and the sheet ends
        // up unscrollable. Taps fall through to the outer handler, which has to
        // add the scroll offset to land in the text's own coordinates.
        modifier = modifier
            .then(
                if (onPick == null) Modifier else Modifier.pointerInput(window) {
                    detectTapGestures { position ->
                        val result = layout ?: return@detectTapGestures
                        val inText = position.copy(y = position.y + scroll.value)
                        window.wordAt(result.getOffsetForPosition(inText))?.let(onPick)
                    }
                }
            )
            .verticalScroll(scroll),
    )
}

/** The rendered slice of a paragraph, and how its characters map back to words. */
private class ParagraphWindow(
    val text: AnnotatedString,
    /** Character offset each word starts at, parallel to [firstWord]. */
    private val wordStarts: IntArray,
    private val firstWord: Int,
    val offsetOfCurrent: Int?,
) {
    fun wordAt(offset: Int): Int? {
        if (wordStarts.isEmpty()) return null
        val found = wordStarts.binarySearch(offset)
        val slot = if (found >= 0) found else (-found - 2).coerceAtLeast(0)
        return firstWord + slot
    }
}

/** How much of a long paragraph is worth drawing on either side of the reader. */
private const val WORDS_BEHIND = 250
private const val WORDS_AHEAD = 450

private fun buildParagraphWindow(
    tokens: List<Token>,
    paragraphStart: Int,
    paragraphCount: Int,
    current: Int,
    currentSpan: SpanStyle,
): ParagraphWindow {
    val paragraphEnd = (paragraphStart + paragraphCount).coerceAtMost(tokens.size)
    if (paragraphStart >= paragraphEnd) {
        return ParagraphWindow(AnnotatedString(""), IntArray(0), paragraphStart, null)
    }
    val anchor = current.coerceIn(paragraphStart, paragraphEnd - 1)
    val from = maxOf(paragraphStart, anchor - WORDS_BEHIND)
    val to = minOf(paragraphEnd, anchor + WORDS_AHEAD)

    val starts = IntArray(to - from)
    var offsetOfCurrent: Int? = null
    val text = buildAnnotatedString {
        if (from > paragraphStart) append("… ")
        for (i in from until to) {
            starts[i - from] = length
            if (i == current) {
                offsetOfCurrent = length
                withStyle(currentSpan) { append(tokens[i].text) }
            } else {
                append(tokens[i].text)
            }
            if (i < to - 1) append(' ')
        }
        if (to < paragraphEnd) append(" …")
    }
    return ParagraphWindow(text, starts, from, offsetOfCurrent)
}

private fun bookmarkMeta(vm: LecternViewModel, mark: SavedBookmark): String {
    val total = vm.engine.tokens.size
    val percent = if (total > 1) (mark.index * 100 / (total - 1)) else 0
    return if (mark.note.isBlank()) "$percent%" else "$percent% · ${mark.note}"
}

private fun chapterMeta(engine: ReaderEngine, start: Int, end: Int, selected: Boolean): String {
    val words = (end - start).coerceAtLeast(0)
    val minutes = Math.ceil(words.toDouble() / engine.wpm).toInt()
    val length = if (minutes > 1) "$minutes min" else "under a minute"
    return if (selected) "current · $length" else length
}

private fun timeLeft(engine: ReaderEngine): String {
    if (engine.tokens.isEmpty()) return ""
    val minutes = Math.ceil(engine.remainingMs() / 60_000.0).toInt()
    return if (minutes > 1) "$minutes min left" else "under a minute left"
}

private fun trimZeros(value: Float): String =
    "%.2f".format(value).trimEnd('0').trimEnd('.')
