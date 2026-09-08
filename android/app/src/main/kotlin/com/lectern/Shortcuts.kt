package com.lectern

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.lectern.data.ShelfEntry

/**
 * Long-pressing the launcher icon offers the books you were last reading, each
 * opening straight into the reader at your place.
 */
fun updateBookShortcuts(context: Context, shelf: List<ShelfEntry>) {
    val recent = shelf
        .filter { it.openedAt > 0 && !it.finished }
        .sortedByDescending { it.openedAt }
        .take(MAX_SHORTCUTS)

    val shortcuts = recent.map { entry ->
        val label = entry.title.ifBlank { "Untitled" }
        ShortcutInfoCompat.Builder(context, "book-${entry.id}")
            .setShortLabel(label.take(24))
            .setLongLabel(label.take(48))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_book))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_BOOK_ID, entry.id)
                }
            )
            .build()
    }

    // A device can refuse shortcuts (rate limits, restricted profiles); the app
    // works the same either way.
    runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
}

private const val MAX_SHORTCUTS = 3
