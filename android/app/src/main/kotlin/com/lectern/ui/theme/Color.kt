package com.lectern.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lectern.data.Palette

/**
 * The hand-made palettes, used where the platform can't hand us a wallpaper
 * scheme (below Android 12) or where the reader has turned Material You off.
 *
 * Each is described by a handful of colours — a ground, an ink and one accent —
 * and the rest of the Material roles are mixed from those, so a palette stays
 * consistent without spelling out forty values four times over.
 */
private class Recipe(
    val ground: Color,
    val ink: Color,
    val accent: Color,
    val onAccent: Color,
    /** Slightly lifted surfaces: cards, sheets, the top bar. */
    val raised: Color,
    val dark: Boolean,
)

private fun Recipe.scheme(): ColorScheme {
    val muted = lerp(ink, ground, 0.35f)
    val faint = lerp(ink, ground, 0.55f)
    val line = lerp(ink, ground, 0.78f)
    val container = lerp(accent, ground, if (dark) 0.72f else 0.78f)
    val onContainer = lerp(accent, ink, if (dark) 0.25f else 0.7f)
    val secondary = lerp(ink, accent, 0.25f)
    fun step(level: Float) = lerp(ground, raised, level)

    val roles = ColorRoles(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = lerp(accent, ground, 0.4f),
        secondary = secondary,
        onSecondary = ground,
        secondaryContainer = step(1.6f),
        onSecondaryContainer = ink,
        tertiary = lerp(accent, muted, 0.5f),
        onTertiary = onAccent,
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        surfaceVariant = step(1.8f),
        onSurfaceVariant = muted,
        surfaceContainerLowest = step(-0.4f),
        surfaceContainerLow = step(0.6f),
        surfaceContainer = step(1f),
        surfaceContainerHigh = step(1.5f),
        surfaceContainerHighest = step(2f),
        surfaceBright = step(if (dark) 2.4f else 0.2f),
        surfaceDim = step(if (dark) -0.3f else 1.4f),
        outline = faint,
        outlineVariant = line,
        inverseSurface = ink,
        inverseOnSurface = ground,
        error = if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A),
        onError = if (dark) Color(0xFF690005) else Color(0xFFFFFFFF),
        errorContainer = if (dark) Color(0xFF93000A) else Color(0xFFFFDAD6),
        onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002),
    )
    return roles.toScheme()
}

/** Every Material role this app paints, so none fall back to the baseline. */
private class ColorRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

/**
 * Material's light and dark builders differ only in the defaults they fill in;
 * every role here is given explicitly — including the fixed-accent ones — so
 * one builder serves both, and nothing falls through to the baseline purple.
 */
private fun ColorRoles.toScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = primary,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    surfaceBright = surfaceBright,
    surfaceDim = surfaceDim,
    outline = outline,
    outlineVariant = outlineVariant,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    scrim = Color(0xFF000000),
    primaryFixed = primaryContainer,
    primaryFixedDim = lerp(primaryContainer, primary, 0.3f),
    onPrimaryFixed = onPrimaryContainer,
    onPrimaryFixedVariant = onPrimaryContainer,
    secondaryFixed = secondaryContainer,
    secondaryFixedDim = lerp(secondaryContainer, secondary, 0.3f),
    onSecondaryFixed = onSecondaryContainer,
    onSecondaryFixedVariant = onSecondaryContainer,
    tertiaryFixed = tertiaryContainer,
    tertiaryFixedDim = lerp(tertiaryContainer, tertiary, 0.3f),
    onTertiaryFixed = onTertiaryContainer,
    onTertiaryFixedVariant = onTertiaryContainer,
)

private val LampDark = Recipe(
    ground = Color(0xFF171310),
    ink = Color(0xFFF4EEDD),
    accent = Color(0xFFFFB527),
    onAccent = Color(0xFF2A1C00),
    raised = Color(0xFF241E16),
    dark = true,
)

private val LampLight = Recipe(
    ground = Color(0xFFFBF6EC),
    ink = Color(0xFF211C15),
    accent = Color(0xFF7A5200),
    onAccent = Color(0xFFFFFFFF),
    raised = Color(0xFFEFE7D6),
    dark = false,
)

private val PaperDark = Recipe(
    ground = Color(0xFF1B1A18),
    ink = Color(0xFFE9E3D6),
    accent = Color(0xFFD98C74),
    onAccent = Color(0xFF3A1B10),
    raised = Color(0xFF272521),
    dark = true,
)

private val PaperLight = Recipe(
    ground = Color(0xFFF6F1E4),
    ink = Color(0xFF2E2A24),
    accent = Color(0xFF8E3B23),
    onAccent = Color(0xFFFFFFFF),
    raised = Color(0xFFE8E1D0),
    dark = false,
)

private val MidnightDark = Recipe(
    ground = Color(0xFF000000),
    ink = Color(0xFFEDEFF3),
    accent = Color(0xFF9FC7FF),
    onAccent = Color(0xFF002F5E),
    raised = Color(0xFF15171A),
    dark = true,
)

private val MidnightLight = Recipe(
    ground = Color(0xFFF7F8FA),
    ink = Color(0xFF191C20),
    accent = Color(0xFF1A5FA8),
    onAccent = Color(0xFFFFFFFF),
    raised = Color(0xFFE7EAEF),
    dark = false,
)

private val EmberDark = Recipe(
    ground = Color(0xFF0B0605),
    ink = Color(0xFFE8B3A4),
    accent = Color(0xFFFF6B4A),
    onAccent = Color(0xFF2B0A02),
    raised = Color(0xFF1A0D0A),
    dark = true,
)

private val EmberLight = Recipe(
    ground = Color(0xFFFDF3EF),
    ink = Color(0xFF3B1C14),
    accent = Color(0xFFA33113),
    onAccent = Color(0xFFFFFFFF),
    raised = Color(0xFFF2E0D9),
    dark = false,
)

/** The colour scheme for a palette, in the requested mode. */
fun paletteScheme(palette: Palette, dark: Boolean): ColorScheme = when (palette) {
    Palette.Lamp -> if (dark) LampDark else LampLight
    Palette.Paper -> if (dark) PaperDark else PaperLight
    Palette.Midnight -> if (dark) MidnightDark else MidnightLight
    Palette.Ember -> if (dark) EmberDark else EmberLight
}.scheme()

/**
 * The pivot letter's colour: the theme's accent, pulled toward white or black
 * so it can be read against any palette, wallpaper or ambient light. 0f is the
 * accent untouched.
 */
fun pivotColor(base: Color, shade: Float): Color = when {
    shade > 0f -> lerp(base, Color.White, shade.coerceAtMost(1f))
    shade < 0f -> lerp(base, Color.Black, (-shade).coerceAtMost(1f))
    else -> base
}
