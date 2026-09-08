package com.lectern.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lectern.core.ReaderEngine
import com.lectern.data.Palette
import com.lectern.data.PivotStyle
import com.lectern.data.ReadingFont
import com.lectern.data.ThemeMode
import com.lectern.data.VolumeKeys
import com.lectern.data.WordSize
import com.lectern.ui.LecternViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LecternViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shelf by vm.shelf.collectAsStateWithLifecycle()
    var restoring by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(vm::exportShelf) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importShelf) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item { SectionLabel("Pace") }

            item {
                SliderRow(
                    title = "Speed",
                    value = "${settings.wpm} wpm",
                    sliderValue = settings.wpm.toFloat(),
                    range = ReaderEngine.MIN_WPM.toFloat()..ReaderEngine.MAX_WPM.toFloat(),
                    steps = (ReaderEngine.MAX_WPM - ReaderEngine.MIN_WPM) / ReaderEngine.WPM_STEP - 1,
                    description = "Starting speed for a new book. The ± buttons in the reader change it too.",
                ) { wpm ->
                    val stepped = (wpm / ReaderEngine.WPM_STEP).roundToInt() * ReaderEngine.WPM_STEP
                    vm.updateSettings {
                        it.copy(wpm = stepped.coerceIn(ReaderEngine.MIN_WPM, ReaderEngine.MAX_WPM))
                    }
                }
            }

            item {
                SwitchRow(
                    title = "A speed per book",
                    description = "A novel and a spec want different paces.",
                    checked = settings.perBookSpeed,
                ) { on -> vm.updateSettings { it.copy(perBookSpeed = on) } }
            }

            item {
                SliderRow(
                    title = "Pause at a full stop",
                    value = "×" + trimZeros(settings.sentencePause),
                    sliderValue = settings.sentencePause,
                    range = 1f..5f,
                    steps = 15,
                    description = "How long a word ending a sentence holds, counted in normal words.",
                ) { pause -> vm.updateSettings { it.copy(sentencePause = pause) } }
            }

            item {
                SliderRow(
                    title = "Pause at a comma",
                    value = "+" + trimZeros(settings.clausePause),
                    sliderValue = settings.clausePause,
                    range = 0f..2f,
                    steps = 7,
                    description = "Also semicolons, colons and dashes.",
                ) { pause -> vm.updateSettings { it.copy(clausePause = pause) } }
            }

            item {
                SliderRow(
                    title = "Pause at a paragraph",
                    value = "+" + trimZeros(settings.paragraphPause),
                    sliderValue = settings.paragraphPause,
                    range = 0f..3f,
                    steps = 11,
                    description = "Extra pause at the end of a paragraph.",
                ) { pause -> vm.updateSettings { it.copy(paragraphPause = pause) } }
            }

            item {
                SliderRow(
                    title = "Warm-up",
                    value = if (settings.warmUpWords == 0) "off" else "${settings.warmUpWords} words",
                    sliderValue = settings.warmUpWords.toFloat(),
                    range = 0f..500f,
                    steps = 9,
                    description = "Start at 75% speed and reach the dial over this many words.",
                ) { words ->
                    vm.updateSettings { it.copy(warmUpWords = (words / 50).roundToInt() * 50) }
                }
            }

            item {
                SliderRow(
                    title = "Words at a time",
                    value = when (settings.chunkSize) {
                        1 -> "one"
                        2 -> "two"
                        else -> "three"
                    },
                    sliderValue = settings.chunkSize.toFloat(),
                    range = 1f..ReaderEngine.MAX_CHUNK.toFloat(),
                    steps = ReaderEngine.MAX_CHUNK - 2,
                    description = "A chunk never crosses a full stop. Long words group less often.",
                ) { size -> vm.updateSettings { it.copy(chunkSize = size.roundToInt()) } }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Stopping and starting") }

            item {
                SliderRow(
                    title = "Rewind on resume",
                    value = when (settings.rewindWords) {
                        0 -> "off"
                        1 -> "1 word"
                        else -> "${settings.rewindWords} words"
                    },
                    sliderValue = settings.rewindWords.toFloat(),
                    range = 0f..5f,
                    steps = 4,
                    description = "Step back this far when you press play, to rejoin the sentence.",
                ) { words -> vm.updateSettings { it.copy(rewindWords = words.roundToInt()) } }
            }

            item {
                SliderRow(
                    title = "Break reminder",
                    value = if (settings.breakMinutes == 0) "off" else "${settings.breakMinutes} min",
                    sliderValue = settings.breakMinutes.toFloat(),
                    range = 0f..60f,
                    steps = 11,
                    description = "Pause on its own after reading this long.",
                ) { minutes ->
                    vm.updateSettings { it.copy(breakMinutes = (minutes / 5).roundToInt() * 5) }
                }
            }

            item {
                SwitchRow(
                    title = "Stop at the end of a chapter",
                    description = "Instead of reading into the next one.",
                    checked = settings.stopAtChapterEnd,
                ) { on -> vm.updateSettings { it.copy(stopAtChapterEnd = on) } }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("The stage") }

            item {
                ChipRow(
                    title = "Typeface",
                    options = ReadingFont.entries,
                    selected = settings.font,
                    label = { it.label },
                    description = settings.font.description,
                ) { font -> vm.updateSettings { it.copy(font = font) } }
            }

            item {
                ChipRow(
                    title = "Word size",
                    options = WordSize.entries,
                    selected = settings.wordSize,
                    label = { it.label },
                    description = "How large the words are drawn.",
                ) { size -> vm.updateSettings { it.copy(wordSize = size) } }
            }

            item {
                ChipRow(
                    title = "Pivot letter",
                    options = PivotStyle.entries,
                    selected = settings.pivotStyle,
                    label = { it.label },
                    description = "How the letter your eye lands on is marked.",
                ) { style -> vm.updateSettings { it.copy(pivotStyle = style) } }
            }

            if (settings.pivotStyle == PivotStyle.Colour) {
                item {
                    SliderRow(
                        title = "Pivot shade",
                        value = shadeLabel(settings.pivotShade),
                        sliderValue = settings.pivotShade,
                        range = -0.7f..0.7f,
                        steps = 13,
                        description = "Darkens or lightens the marked letter against " +
                            "whatever colour the theme gives it.",
                    ) { shade ->
                        vm.updateSettings { it.copy(pivotShade = (shade * 10).roundToInt() / 10f) }
                    }
                }
            }

            item {
                SwitchRow(
                    title = "Reading rails",
                    description = "The two lines and the tick that mark the pivot column.",
                    checked = settings.showRails,
                ) { on -> vm.updateSettings { it.copy(showRails = on) } }
            }

            item {
                SwitchRow(
                    title = "Context line",
                    description = "Shows the current sentence under the word, dimmed.",
                    checked = settings.contextLine,
                ) { on -> vm.updateSettings { it.copy(contextLine = on) } }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Controls") }

            item {
                SwitchRow(
                    title = "Tap zones",
                    description = "Tap the middle to play or pause. The sides step a sentence.",
                    checked = settings.tapZones,
                ) { on -> vm.updateSettings { it.copy(tapZones = on) } }
            }

            item {
                SwitchRow(
                    title = "Hold to peek",
                    description = "Hold a finger on the stage to see the paragraph you are in.",
                    checked = settings.holdToPeek,
                ) { on -> vm.updateSettings { it.copy(holdToPeek = on) } }
            }

            item {
                ChipRow(
                    title = "Volume keys",
                    options = VolumeKeys.entries,
                    selected = settings.volumeKeys,
                    label = { it.label },
                    description = "For reading with the phone propped up. A hardware keyboard also " +
                        "works: space, arrows, B to bookmark.",
                ) { keys -> vm.updateSettings { it.copy(volumeKeys = keys) } }
            }

            item {
                SwitchRow(
                    title = "Keep the screen on",
                    description = "While words are moving.",
                    checked = settings.keepScreenOn,
                ) { on -> vm.updateSettings { it.copy(keepScreenOn = on) } }
            }

            item {
                SwitchRow(
                    title = "Dim the controls while reading",
                    description = "Fades the controls while words are moving.",
                    checked = settings.dimChrome,
                ) { on -> vm.updateSettings { it.copy(dimChrome = on) } }
            }

            item {
                SwitchRow(
                    title = "Full screen",
                    description = "Hides the status and navigation bars while a book is open.",
                    checked = settings.fullScreen,
                ) { on -> vm.updateSettings { it.copy(fullScreen = on) } }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Appearance") }

            item {
                ChipRow(
                    title = "Theme",
                    options = ThemeMode.entries,
                    selected = settings.themeMode,
                    label = { it.name },
                    description = null,
                ) { mode -> vm.updateSettings { it.copy(themeMode = mode) } }
            }

            item {
                SwitchRow(
                    title = "Material You colours",
                    description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Take the colours, including the pivot letter, from your wallpaper."
                    } else {
                        "Needs Android 12 or newer. A palette below is used instead."
                    },
                    checked = settings.dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                ) { on -> vm.updateSettings { it.copy(dynamicColor = on) } }
            }

            if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                item {
                    ChipRow(
                        title = "Palette",
                        options = Palette.entries,
                        selected = settings.palette,
                        label = { it.label },
                        description = settings.palette.description,
                    ) { palette -> vm.updateSettings { it.copy(palette = palette) } }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionLabel("Your books") }

            item {
                val words = shelf.sumOf { it.index }
                InfoRow(
                    title = "Shelf",
                    body = "${shelf.size} ${if (shelf.size == 1) "book" else "books"} · " +
                        "$words words read",
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { exporter.launch("lectern-backup.zip") }) {
                        Text("Back up")
                    }
                    OutlinedButton(onClick = { restoring = true }) {
                        Text("Restore")
                    }
                }
            }

            item {
                InfoRow(
                    title = "On this device",
                    body = "Files are parsed on your phone and never uploaded. Lectern uses the " +
                        "network once: when you give it a link to read.",
                )
            }

            item {
                InfoRow(
                    title = "Licence",
                    body = "GPL-3.0-or-later. Free and open source; the code is at " +
                        "github.com/Sheepheerd/lectern.",
                )
            }

            item {
                InfoRow(
                    title = "Atkinson Hyperlegible",
                    body = "© Braille Institute of America, used under the SIL Open Font " +
                        "License 1.1.",
                )
            }

            item {
                Text(
                    text = "One word at a time, in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }

    if (restoring) {
        AlertDialog(
            onDismissRequest = { restoring = false },
            title = { Text("Restore a backup?") },
            text = {
                Text(
                    "Replaces everything on the shelf now: books, positions and bookmarks."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoring = false
                    importer.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("Choose a file") }
            },
            dismissButton = { TextButton(onClick = { restoring = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun <T> ChipRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: String?,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (option in options) {
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    onChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "20% darker", "theme colour", "40% lighter". */
private fun shadeLabel(shade: Float): String {
    val percent = (kotlin.math.abs(shade) * 100).roundToInt()
    return when {
        percent == 0 -> "theme colour"
        shade < 0 -> "$percent% darker"
        else -> "$percent% lighter"
    }
}

private fun trimZeros(value: Float): String =
    "%.2f".format(value).trimEnd('0').trimEnd('.')
