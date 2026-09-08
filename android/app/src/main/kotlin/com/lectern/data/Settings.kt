package com.lectern.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

/** The hand-made palettes, used when Material You is off. */
enum class Palette(val label: String, val description: String) {
    Lamp("Lamp", "Warm near-black with an amber accent"),
    Paper("Paper", "Cream ground, soft ink, a muted red"),
    Midnight("Midnight", "True black for OLED, cool white type"),
    Ember("Ember", "Dim red on black, for reading in the dark"),
}

/** Typefaces offered for the word on the stage. */
enum class ReadingFont(val label: String, val description: String) {
    Mono("Monospace", "The default. Every word sits in the same column"),
    Sans("Sans", "The system text face"),
    Serif("Serif", "More letter shape, like print"),
    Hyperlegible("Hyperlegible", "Atkinson Hyperlegible, drawn for low vision"),
}

/** How large the word on the stage is drawn. */
enum class WordSize(val label: String, val charsAcross: Float) {
    Small("Small", 19f),
    Medium("Medium", 15f),
    Large("Large", 11f),
    Huge("Huge", 8.5f),
}

/** How the optimal-recognition-point letter is marked. */
enum class PivotStyle(val label: String) {
    Colour("Colour"),
    Underline("Underline"),
    Bold("Bold"),
    None("No marking"),
}

/** What the volume keys do while a book is open. */
enum class VolumeKeys(val label: String) {
    Off("Nothing"),
    Speed("Change speed"),
    Sentences("Skip sentences"),
}

/** How the shelf is ordered. */
enum class ShelfOrder(val label: String) {
    Recent("Recently opened"),
    Added("Recently added"),
    Title("Title"),
    Progress("Least finished"),
}

@Immutable
data class AppSettings(
    // Reading
    val wpm: Int = 300,
    /** How many base word-times a sentence-ending word occupies. */
    val sentencePause: Float = 2.7f,
    /** Extra word-times at a comma, semicolon or dash. */
    val clausePause: Float = 0.6f,
    /** Extra word-times at the end of a paragraph. */
    val paragraphPause: Float = 1.2f,
    /** Words to step back when reading resumes. */
    val rewindWords: Int = 2,
    /** Words shown at once, 1–3. */
    val chunkSize: Int = 1,
    /** Words spent ramping up to full speed after each resume; 0 is off. */
    val warmUpWords: Int = 0,
    /** Auto-pause after this many minutes of reading; 0 is off. */
    val breakMinutes: Int = 0,
    val stopAtChapterEnd: Boolean = false,
    /** Remember a speed per book rather than one speed for everything. */
    val perBookSpeed: Boolean = true,

    // The stage
    val font: ReadingFont = ReadingFont.Mono,
    val wordSize: WordSize = WordSize.Medium,
    val pivotStyle: PivotStyle = PivotStyle.Colour,
    val showRails: Boolean = true,
    /** The sentence you're inside, dimmed, under the word. */
    val contextLine: Boolean = false,

    // Controls
    val tapZones: Boolean = false,
    val holdToPeek: Boolean = true,
    val volumeKeys: VolumeKeys = VolumeKeys.Off,
    val keepScreenOn: Boolean = true,
    val dimChrome: Boolean = true,
    val fullScreen: Boolean = false,

    // Appearance
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val palette: Palette = Palette.Lamp,

    // Shelf
    val shelfOrder: ShelfOrder = ShelfOrder.Recent,

    // First run
    /** A book has been opened at least once, so the welcome has done its job. */
    val hasReadSomething: Boolean = false,
    /** The one-time line explaining the pivot letter has been shown. */
    val seenPivotHint: Boolean = false,
)

/**
 * Settings live in SharedPreferences rather than DataStore on purpose: the
 * theme has to be known on the first frame, and a synchronous read avoids
 * painting the wrong one and swapping it a moment later.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("lectern", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    private fun read() = AppSettings(
        wpm = prefs.getInt("wpm", 300),
        sentencePause = prefs.getFloat("sentencePause", 2.7f),
        clausePause = prefs.getFloat("clausePause", 0.6f),
        paragraphPause = prefs.getFloat("paragraphPause", 1.2f),
        rewindWords = prefs.getInt("rewindWords", 2),
        chunkSize = prefs.getInt("chunkSize", 1),
        warmUpWords = prefs.getInt("warmUpWords", 0),
        breakMinutes = prefs.getInt("breakMinutes", 0),
        stopAtChapterEnd = prefs.getBoolean("stopAtChapterEnd", false),
        perBookSpeed = prefs.getBoolean("perBookSpeed", true),
        font = enumOr("font", ReadingFont.Mono),
        wordSize = enumOr("wordSize", WordSize.Medium),
        pivotStyle = enumOr("pivotStyle", PivotStyle.Colour),
        showRails = prefs.getBoolean("showRails", true),
        contextLine = prefs.getBoolean("contextLine", false),
        tapZones = prefs.getBoolean("tapZones", false),
        holdToPeek = prefs.getBoolean("holdToPeek", true),
        volumeKeys = enumOr("volumeKeys", VolumeKeys.Off),
        keepScreenOn = prefs.getBoolean("keepScreenOn", true),
        dimChrome = prefs.getBoolean("dimChrome", true),
        fullScreen = prefs.getBoolean("fullScreen", false),
        themeMode = enumOr("themeMode", ThemeMode.System),
        dynamicColor = prefs.getBoolean("dynamicColor", true),
        palette = enumOr("palette", Palette.Lamp),
        shelfOrder = enumOr("shelfOrder", ShelfOrder.Recent),
        hasReadSomething = prefs.getBoolean("hasReadSomething", false),
        seenPivotHint = prefs.getBoolean("seenPivotHint", false),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        if (next == _state.value) return
        prefs.edit()
            .putInt("wpm", next.wpm)
            .putFloat("sentencePause", next.sentencePause)
            .putFloat("clausePause", next.clausePause)
            .putFloat("paragraphPause", next.paragraphPause)
            .putInt("rewindWords", next.rewindWords)
            .putInt("chunkSize", next.chunkSize)
            .putInt("warmUpWords", next.warmUpWords)
            .putInt("breakMinutes", next.breakMinutes)
            .putBoolean("stopAtChapterEnd", next.stopAtChapterEnd)
            .putBoolean("perBookSpeed", next.perBookSpeed)
            .putString("font", next.font.name)
            .putString("wordSize", next.wordSize.name)
            .putString("pivotStyle", next.pivotStyle.name)
            .putBoolean("showRails", next.showRails)
            .putBoolean("contextLine", next.contextLine)
            .putBoolean("tapZones", next.tapZones)
            .putBoolean("holdToPeek", next.holdToPeek)
            .putString("volumeKeys", next.volumeKeys.name)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putBoolean("dimChrome", next.dimChrome)
            .putBoolean("fullScreen", next.fullScreen)
            .putString("themeMode", next.themeMode.name)
            .putBoolean("dynamicColor", next.dynamicColor)
            .putString("palette", next.palette.name)
            .putString("shelfOrder", next.shelfOrder.name)
            .putBoolean("hasReadSomething", next.hasReadSomething)
            .putBoolean("seenPivotHint", next.seenPivotHint)
            .apply()
        _state.value = next
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, fallback: T): T =
        prefs.getString(key, null)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
