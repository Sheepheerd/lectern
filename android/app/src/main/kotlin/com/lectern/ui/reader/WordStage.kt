package com.lectern.ui.reader

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lectern.core.Chunk
import com.lectern.data.PivotStyle
import com.lectern.data.ReadingFont
import com.lectern.data.WordSize
import com.lectern.ui.theme.family
import com.lectern.ui.theme.pivotColor
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** What a tap on the stage did, so the reader can respond to the right thing. */
enum class StageTap { Toggle, Previous, Next }

/**
 * The stage: the words in this flash, positioned so the optimal-recognition-
 * point letter sits on the tick between the two rails. The eye never travels.
 */
@Composable
fun WordStage(
    chunk: Chunk?,
    modifier: Modifier = Modifier,
    font: ReadingFont = ReadingFont.Mono,
    wordSize: WordSize = WordSize.Medium,
    pivotStyle: PivotStyle = PivotStyle.Colour,
    pivotShade: Float = 0f,
    showRails: Boolean = true,
    tapZones: Boolean = false,
    holdToPeek: Boolean = false,
    onTap: (StageTap) -> Unit = {},
    onSpeed: (faster: Boolean) -> Unit = {},
    onPeek: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .pointerInput(tapZones, holdToPeek) {
                detectTapGestures(
                    onPress = {
                        if (!holdToPeek) return@detectTapGestures
                        // A press that outlasts a tap opens a peek at the
                        // paragraph, and lifting a finger closes it again.
                        val quick = withTimeoutOrNull(PEEK_DELAY_MS) { tryAwaitRelease() }
                        if (quick == null) {
                            onPeek(true)
                            tryAwaitRelease()
                            onPeek(false)
                        }
                    },
                    onTap = { offset ->
                        if (!tapZones) {
                            onTap(StageTap.Toggle)
                        } else {
                            val third = size.width / 3f
                            onTap(
                                when {
                                    offset.x < third -> StageTap.Previous
                                    offset.x > size.width - third -> StageTap.Next
                                    else -> StageTap.Toggle
                                }
                            )
                        }
                    },
                )
            }
            // Sideways for sentences, up and down for speed: the controls stay
            // reachable without looking away from the word.
            .pointerInput(density) {
                val threshold = with(density) { SWIPE_THRESHOLD.toPx() }
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        if (abs(dx) > abs(dy)) {
                            // Swiping left carries you forward, as a page does.
                            if (abs(dx) > threshold) {
                                onTap(if (dx < 0f) StageTap.Next else StageTap.Previous)
                            }
                        } else {
                            if (abs(dy) > threshold) onSpeed(dy < 0f)
                        }
                    },
                ) { change, drag ->
                    change.consume()
                    dx += drag.x
                    dy += drag.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Size the words to the reader's choice, but never wider than the
        // stage. The pivot letter is pinned to the centre, so what has to fit
        // is twice the longer half of the chunk — not its total length.
        val text = chunk?.text.orEmpty()
        val orp = chunk?.orp ?: 0
        val longerHalf = maxOf(orp + 1, text.length - orp)
        val chars = maxOf(wordSize.charsAcross, 2f * longerHalf)
        val fontSize = (maxWidth.value / (chars * MONO_ADVANCE)).coerceIn(18f, 72f).sp
        val railWidth = maxWidth * 0.88f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (showRails) Rail(width = railWidth, tickBelow = true)
            PivotWord(
                chunk = chunk,
                pivotStyle = pivotStyle,
                pivotShade = pivotShade,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = font.family(),
                    fontSize = fontSize,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
            )
            if (showRails) Rail(width = railWidth, tickBelow = false)
        }
    }
}

/** The words, placed so the pivot letter straddles the centre line. */
@Composable
private fun PivotWord(
    chunk: Chunk?,
    pivotStyle: PivotStyle,
    pivotShade: Float,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val text = chunk?.text.orEmpty()
    val orp = chunk?.orp ?: 0
    val pivot = if (orp < text.length) text[orp].toString() else ""

    val pivotStyleApplied = when (pivotStyle) {
        PivotStyle.Colour ->
            style.copy(color = pivotColor(MaterialTheme.colorScheme.primary, pivotShade))
        PivotStyle.Underline -> style.copy(textDecoration = TextDecoration.Underline)
        PivotStyle.Bold -> style.copy(fontWeight = FontWeight.Bold)
        PivotStyle.None -> style
    }

    Layout(
        modifier = modifier.semantics { contentDescription = text },
        content = {
            Text(text.take(orp), style = style, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
            Text(pivot, style = pivotStyleApplied, maxLines = 1, softWrap = false)
            Text(text.drop(orp + 1), style = style, maxLines = 1, softWrap = false)
        },
    ) { measurables, constraints ->
        val parts = measurables.map { it.measure(Constraints()) }
        val (pre, mid, post) = parts
        val width = constraints.maxWidth
        val height = parts.maxOf { it.height }
        layout(width, height) {
            val pivotStart = width / 2 - mid.width / 2
            pre.placeRelative(pivotStart - pre.width, 0)
            mid.placeRelative(pivotStart, 0)
            post.placeRelative(pivotStart + mid.width, 0)
        }
    }
}

/** A reading rail: a hairline with a tick marking the pivot column. */
@Composable
private fun Rail(width: Dp, tickBelow: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val tick = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .width(width)
            .height(RAIL_HEIGHT)
            .drawBehind {
                val y = if (tickBelow) 0f else size.height
                val tickEnd = if (tickBelow) size.height else 0f
                drawLine(
                    color = line,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = tick,
                    start = Offset(size.width / 2f, y),
                    end = Offset(size.width / 2f, tickEnd),
                    strokeWidth = 1.dp.toPx(),
                )
            }
    )
}

private val RAIL_HEIGHT = 9.dp

/** How far a drag has to travel before it counts as a swipe. */
private val SWIPE_THRESHOLD = 48.dp

/** How long a press has to be held before it peeks at the paragraph. */
private const val PEEK_DELAY_MS = 350L

/** Advance width of a monospace glyph as a fraction of its font size. */
private const val MONO_ADVANCE = 0.62f
