package com.lectern.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lectern.core.DEMO_LINES
import com.lectern.core.Punct
import com.lectern.core.Section
import com.lectern.core.Token
import com.lectern.core.chunkAt
import com.lectern.core.tokenize
import com.lectern.data.AppSettings
import com.lectern.ui.reader.WordStage
import kotlinx.coroutines.delay

/**
 * What a stranger meets on a shelf with nothing on it. Rather than describing
 * how the reader works, it runs: the real stage, the real pivot alignment, the
 * real pacing, reading a passage about what is happening while it happens.
 *
 * It plays once. Nobody wants a paragraph looping on their shelf forever.
 */
@Composable
fun Welcome(
    settings: AppSettings,
    onReadSample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = remember { tokenize(listOf(Section("", DEMO_LINES))).tokens }
    // Saved, so scrolling the card out of view and back does not restart it.
    var index by rememberSaveable { mutableIntStateOf(0) }
    var playing by rememberSaveable { mutableStateOf(true) }

    val finished = index >= tokens.lastIndex
    val chunk = chunkAt(tokens, index.coerceAtMost(tokens.lastIndex), 1)

    LaunchedEffect(playing, index) {
        if (!playing || finished) return@LaunchedEffect
        delay(holdFor(tokens[index]))
        index++
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WordStage(
                chunk = chunk,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(12.dp)),
                font = settings.font,
                wordSize = settings.wordSize,
                pivotStyle = settings.pivotStyle,
                showRails = settings.showRails,
                onTap = {
                    if (finished) index = 0
                    playing = !finished && !playing || finished
                },
            )

            // The line under the stage carries the demo's state, so the card
            // never sits silent with no way back into it.
            val hint = when {
                finished -> "Tap the words to run that again"
                playing -> "Your eyes have not moved"
                else -> "Tap the words to carry on"
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(animateFloatAsState(if (playing && !finished) 0.55f else 1f, label = "hint").value),
            )

            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Books, one word at a time",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Lectern reads a PDF, an EPUB, a document or an article to you " +
                        "this way, holding the letter your eye rests on in one column. " +
                        "Everything is parsed on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Button(onClick = onReadSample) { Text("Read the rest") }
            Text(
                text = "About three minutes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** The demo's own clock: 260 wpm, with the sentence pauses the reader uses. */
private fun holdFor(token: Token): Long {
    var multiplier = token.weight.toDouble()
    when (token.punct) {
        Punct.SENTENCE -> multiplier += 1.7
        Punct.CLAUSE -> multiplier += 0.6
        Punct.NONE -> Unit
    }
    if (token.paraEnd) multiplier += 1.2
    return ((60_000.0 / 260.0) * multiplier).toLong()
}
