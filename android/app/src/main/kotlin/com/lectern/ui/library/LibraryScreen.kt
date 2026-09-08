package com.lectern.ui.library

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lectern.data.ShelfEntry
import com.lectern.data.ShelfFilter
import com.lectern.data.ShelfOrder
import com.lectern.data.filteredBy
import com.lectern.data.sortedBy
import com.lectern.ui.LecternViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val OPEN_TYPES = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "text/markdown",
    "text/html",
    // FictionBook and anything a picker can't type: the parser goes by name.
    "application/octet-stream",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LecternViewModel,
    onOpenBook: (ShelfEntry) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf<ShelfFilter>(ShelfFilter.All) }
    val books = remember(shelf, settings.shelfOrder, filter) {
        shelf.filteredBy(filter).sortedBy(settings.shelfOrder)
    }
    val tags = remember(shelf) { vm.tags() }
    val continueReading = remember(shelf) { vm.continueReading() }

    var pasting by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ShelfEntry?>(null) }
    var renaming by remember { mutableStateOf<ShelfEntry?>(null) }
    var tagging by remember { mutableStateOf<ShelfEntry?>(null) }
    var removing by remember { mutableStateOf<ShelfEntry?>(null) }
    var sorting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importDocument)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Wordmark() },
                actions = {
                    Box {
                        IconButton(onClick = { sorting = true }, enabled = shelf.size > 1) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort the shelf")
                        }
                        DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                            for (order in ShelfOrder.entries) {
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = {
                                        RadioButton(
                                            selected = order == settings.shelfOrder,
                                            onClick = null,
                                        )
                                    },
                                    onClick = {
                                        vm.updateSettings { it.copy(shelfOrder = order) }
                                        sorting = false
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            // One column on a phone, more as the window grows.
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (continueReading != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                    ContinueCard(vm, continueReading) { onOpenBook(continueReading) }
                }
            }

            if (shelf.isEmpty() && !settings.hasReadSomething) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "welcome") {
                    Welcome(settings = settings, onReadSample = vm::importSample)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "open") {
                OpenBookCard(
                    busy = vm.importing,
                    onOpenFile = { if (!vm.importing) picker.launch(OPEN_TYPES) },
                    onPaste = { pasting = true },
                    onLink = { linking = true },
                )
            }

            if (shelf.isEmpty() && settings.hasReadSomething) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "cleared") {
                    EmptyNote(
                        line = "Nothing on the shelf.",
                        action = "Read the sample again",
                        onAction = vm::importSample,
                    )
                }
            }

            if (shelf.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                    FilterRow(
                        selected = filter,
                        tags = tags,
                        hasArchived = shelf.any { it.archived },
                        onSelect = { filter = it },
                    )
                }
                if (books.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "shelf-label") {
                    Text(
                        text = shelfLabel(books.size, filter),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                if (books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "no-matches") {
                        EmptyNote(
                            line = emptyFilterLine(filter),
                            action = "Show all books",
                            onAction = { filter = ShelfFilter.All },
                        )
                    }
                }
                items(books, key = { it.id }) { entry ->
                    ShelfCard(
                        vm = vm,
                        entry = entry,
                        onOpen = { onOpenBook(entry) },
                        onActions = { actionsFor = entry },
                    )
                }
            }
        }
    }

    if (pasting) {
        ModalBottomSheet(
            onDismissRequest = { pasting = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PasteSheet { text ->
                pasting = false
                vm.importPastedText(text)
            }
        }
    }

    if (linking) {
        LinkDialog(
            onDismiss = { linking = false },
            onRead = { url ->
                linking = false
                vm.importUrl(url)
            },
        )
    }

    actionsFor?.let { entry ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            BookActionsSheet(
                entry = entry,
                onRead = {
                    actionsFor = null
                    onOpenBook(entry)
                },
                onRename = {
                    actionsFor = null
                    renaming = entry
                },
                onTags = {
                    actionsFor = null
                    tagging = entry
                },
                onRestart = {
                    vm.restart(entry)
                    actionsFor = null
                },
                onArchive = {
                    vm.setArchived(entry, !entry.archived)
                    actionsFor = null
                },
                onRemove = {
                    actionsFor = null
                    removing = entry
                },
            )
        }
    }

    renaming?.let { entry ->
        TextFieldDialog(
            title = "Rename",
            initial = entry.title,
            confirm = "Rename",
            onDismiss = { renaming = null },
            onConfirm = { title ->
                vm.rename(entry, title)
                renaming = null
            },
        )
    }

    tagging?.let { entry ->
        TextFieldDialog(
            title = "Tags",
            initial = entry.tags.joinToString(", "),
            confirm = "Save",
            supporting = "Separated by commas: shelf, later, work",
            allowEmpty = true,
            onDismiss = { tagging = null },
            onConfirm = { text ->
                vm.setTags(entry, text.split(","))
                tagging = null
            },
        )
    }

    removing?.let { entry ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this book?") },
            text = { Text("Deletes \"${entry.title}\", your position in it, and its bookmarks.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(entry)
                    removing = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Keep") }
            },
        )
    }
}

/** A quiet line for a shelf, or a filter, with nothing behind it. */
@Composable
private fun EmptyNote(line: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun emptyFilterLine(filter: ShelfFilter): String = when (filter) {
    ShelfFilter.Reading -> "No book is under way."
    ShelfFilter.Finished -> "You have not finished a book yet."
    ShelfFilter.Archived -> "Nothing is archived."
    is ShelfFilter.Tagged -> "Nothing is tagged \"${filter.tag}\"."
    ShelfFilter.All -> "Nothing on the shelf."
}

@Composable
private fun Wordmark() {
    Text(
        text = buildAnnotatedString {
            append("Lect")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("e") }
            append("rn")
        },
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun FilterRow(
    selected: ShelfFilter,
    tags: List<String>,
    hasArchived: Boolean,
    onSelect: (ShelfFilter) -> Unit,
) {
    val builtIn = buildList {
        add(ShelfFilter.All to "All")
        add(ShelfFilter.Reading to "Reading")
        add(ShelfFilter.Finished to "Finished")
        if (hasArchived) add(ShelfFilter.Archived to "Archived")
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(builtIn) { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) },
            )
        }
        items(tags) { tag ->
            val filter = ShelfFilter.Tagged(tag)
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(tag) },
            )
        }
    }
}

/** Picks up the book you were last in, at the word you stopped on. */
@Composable
private fun ContinueCard(vm: LecternViewModel, entry: ShelfEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cover(vm, entry, Modifier.size(width = 48.dp, height = 68.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Continue reading", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${(entry.progress * 100).roundToInt()}% · ${wordsLeft(entry)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun OpenBookCard(
    busy: Boolean,
    onOpenFile: () -> Unit,
    onPaste: () -> Unit,
    onLink: () -> Unit,
) {
    OutlinedCard(
        onClick = onOpenFile,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (busy) "Reading…" else "Open a book",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "PDF · EPUB · DOCX · FB2 · HTML · TXT · MD",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPaste) { Text("Paste text") }
                    TextButton(onClick = onLink) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Read a link")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    vm: LecternViewModel,
    entry: ShelfEntry,
    onOpen: () -> Unit,
    onActions: () -> Unit,
) {
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(vm, entry, Modifier.size(width = 44.dp, height = 62.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        entry.finished -> "finished"
                        entry.started -> "${(entry.progress * 100).roundToInt()}% · ${wordsLeft(entry)}"
                        else -> "not started"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.tags.isNotEmpty()) {
                    Text(
                        text = entry.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.started) {
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                }
            }
            IconButton(onClick = onActions) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More for ${entry.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The book's cover, where the file carried one; otherwise its initial. */
@Composable
private fun Cover(vm: LecternViewModel, entry: ShelfEntry, modifier: Modifier = Modifier) {
    val path = remember(entry.id, entry.cover) { vm.coverPath(entry) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = entry.title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookActionsSheet(
    entry: ShelfEntry,
    onRead: () -> Unit,
    onRename: () -> Unit,
    onTags: () -> Unit,
    onRestart: () -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bookDetails(entry),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SheetAction("Read", onClick = onRead)
        SheetAction("Rename", onClick = onRename)
        SheetAction("Tags", onClick = onTags)
        SheetAction("Start over", enabled = entry.started, onClick = onRestart)
        SheetAction(if (entry.archived) "Unarchive" else "Archive", onClick = onArchive)
        SheetAction("Remove from shelf", destructive = true, onClick = onRemove)
    }
}

@Composable
private fun SheetAction(
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp, horizontal = 8.dp),
        )
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    initial: String,
    confirm: String,
    supporting: String? = null,
    allowEmpty: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = allowEmpty || text.isNotBlank(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onRead: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read a link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("example.com/article") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Lectern fetches the page and keeps the article text. " +
                        "This is the only time it uses the network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRead(url) }, enabled = url.isNotBlank()) { Text("Read") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasteSheet(onRead: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Paste text", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            placeholder = { Text("An article, a chapter, anything.") },
        )
        Button(
            onClick = { onRead(text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Read this")
        }
    }
}

private fun shelfLabel(count: Int, filter: ShelfFilter): String {
    val what = when (filter) {
        ShelfFilter.All -> "On your shelf"
        ShelfFilter.Reading -> "Reading"
        ShelfFilter.Finished -> "Finished"
        ShelfFilter.Archived -> "Archived"
        is ShelfFilter.Tagged -> filter.tag
    }
    return "$what · $count"
}

/** "about 12 min left", from the words remaining at a typical pace. */
private fun wordsLeft(entry: ShelfEntry): String {
    val remaining = (entry.words - entry.index).coerceAtLeast(0)
    if (remaining == 0) return "finished"
    val minutes = Math.ceil(remaining / 300.0).toInt()
    return if (minutes > 1) "about $minutes min left" else "under a minute left"
}

private fun bookDetails(entry: ShelfEntry): String {
    val parts = mutableListOf<String>()
    if (entry.words > 0) parts += "${entry.words} words"
    if (entry.source.isNotBlank()) parts += entry.source
    parts += "added ${relativeDay(entry.addedAt)}"
    return parts.joinToString(" · ")
}

private fun relativeDay(timestamp: Long): String {
    if (timestamp <= 0) return "some time ago"
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp)
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 30 -> "$days days ago"
        else -> "${days / 30} months ago"
    }
}
