package com.lectern.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.lectern.R
import com.lectern.data.ReadingFont

/**
 * Lectern reads in monospace by default: every word occupies a predictable
 * column, which is what lets the pivot letter sit still while the text moves
 * past it. Readers who want a different face — including Atkinson Hyperlegible,
 * drawn for low vision — get it everywhere, not just on the stage.
 */
val HyperlegibleFamily = FontFamily(Font(R.font.atkinson_hyperlegible))

fun ReadingFont.family(): FontFamily = when (this) {
    ReadingFont.Mono -> FontFamily.Monospace
    ReadingFont.Sans -> FontFamily.SansSerif
    ReadingFont.Serif -> FontFamily.Serif
    ReadingFont.Hyperlegible -> HyperlegibleFamily
}

/** The Material 3 type scale, set in the reader's chosen face. */
fun lecternTypography(font: ReadingFont): Typography {
    val family = font.family()
    return Typography().run {
        Typography(
            displayLarge = displayLarge.copy(fontFamily = family),
            displayMedium = displayMedium.copy(fontFamily = family),
            displaySmall = displaySmall.copy(fontFamily = family),
            headlineLarge = headlineLarge.copy(fontFamily = family),
            headlineMedium = headlineMedium.copy(fontFamily = family),
            headlineSmall = headlineSmall.copy(fontFamily = family),
            titleLarge = titleLarge.copy(fontFamily = family),
            titleMedium = titleMedium.copy(fontFamily = family),
            titleSmall = titleSmall.copy(fontFamily = family),
            bodyLarge = bodyLarge.copy(fontFamily = family),
            bodyMedium = bodyMedium.copy(fontFamily = family),
            bodySmall = bodySmall.copy(fontFamily = family),
            labelLarge = labelLarge.copy(fontFamily = family),
            labelMedium = labelMedium.copy(fontFamily = family),
            labelSmall = labelSmall.copy(fontFamily = family),
        )
    }
}
