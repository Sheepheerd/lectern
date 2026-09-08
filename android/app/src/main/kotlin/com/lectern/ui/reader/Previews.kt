package com.lectern.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.lectern.core.Section
import com.lectern.core.chunkAt
import com.lectern.core.tokenize
import com.lectern.data.Palette
import com.lectern.data.ReadingFont
import com.lectern.data.ThemeMode
import com.lectern.data.WordSize
import com.lectern.ui.theme.LecternTheme

/**
 * The stage in isolation, in each palette. Dynamic colour is off here so the
 * previews show Lectern's own schemes rather than the IDE's wallpaper.
 */
@Preview(name = "Lamp · dark", showBackground = true, heightDp = 300)
@Composable
private fun LampDarkPreview() = StagePreview(Palette.Lamp, ThemeMode.Dark)

@Preview(name = "Lamp · light", showBackground = true, heightDp = 300)
@Composable
private fun LampLightPreview() = StagePreview(Palette.Lamp, ThemeMode.Light)

@Preview(name = "Paper", showBackground = true, heightDp = 300)
@Composable
private fun PaperPreview() = StagePreview(Palette.Paper, ThemeMode.Light)

@Preview(name = "Midnight", showBackground = true, heightDp = 300)
@Composable
private fun MidnightPreview() = StagePreview(Palette.Midnight, ThemeMode.Dark)

@Preview(name = "Ember", showBackground = true, heightDp = 300)
@Composable
private fun EmberPreview() = StagePreview(Palette.Ember, ThemeMode.Dark)

@Preview(name = "Hyperlegible, two words", showBackground = true, heightDp = 300)
@Composable
private fun HyperlegiblePreview() =
    StagePreview(Palette.Lamp, ThemeMode.Light, font = ReadingFont.Hyperlegible, chunkSize = 2)

@Composable
private fun StagePreview(
    palette: Palette,
    themeMode: ThemeMode,
    font: ReadingFont = ReadingFont.Mono,
    chunkSize: Int = 1,
) {
    val tokens = tokenize(listOf(Section("", "Reading is a rhythm."))).tokens
    LecternTheme(
        themeMode = themeMode,
        dynamicColor = false,
        palette = palette,
        font = font,
    ) {
        WordStage(
            chunk = chunkAt(tokens, 2, chunkSize),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            font = font,
            wordSize = WordSize.Medium,
        )
    }
}
