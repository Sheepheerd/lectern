package com.lectern.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.lectern.data.Palette
import com.lectern.data.ReadingFont
import com.lectern.data.ThemeMode

/**
 * Material You: on Android 12+ the whole app — including the pivot letter,
 * which takes the primary colour — is tinted from the wallpaper. Turn it off,
 * or run an older device, and one of Lectern's own palettes is used instead.
 */
@Composable
fun LecternTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    palette: Palette = Palette.Lamp,
    font: ReadingFont = ReadingFont.Mono,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> paletteScheme(palette, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = lecternTypography(font),
        content = content,
    )
}
