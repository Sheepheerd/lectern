package com.lectern

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.lectern.core.ReaderEngine
import com.lectern.data.VolumeKeys
import com.lectern.ui.LecternViewModel
import com.lectern.ui.LecternNavHost
import com.lectern.ui.theme.LecternTheme

class MainActivity : ComponentActivity() {

    private val vm: LecternViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val controller = rememberNavController()

            LecternTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                palette = settings.palette,
                font = settings.font,
            ) {
                LecternNavHost(vm = vm, navController = controller)
            }
        }
    }

    /**
     * Volume and keyboard keys drive the reader while a book is open: useful
     * with the phone propped up, and the web app's keys back on a tablet.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.current == null || event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }
        val engine = vm.engine
        val settings = vm.settings.value

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
                when (settings.volumeKeys) {
                    VolumeKeys.Off -> return super.dispatchKeyEvent(event)
                    VolumeKeys.Speed ->
                        vm.nudgeWpm(if (up) ReaderEngine.WPM_STEP else -ReaderEngine.WPM_STEP)

                    VolumeKeys.Sentences ->
                        if (up) engine.previousSentence() else engine.nextSentence()
                }
                return true
            }

            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> engine.toggle()
            KeyEvent.KEYCODE_DPAD_UP -> vm.nudgeWpm(ReaderEngine.WPM_STEP)
            KeyEvent.KEYCODE_DPAD_DOWN -> vm.nudgeWpm(-ReaderEngine.WPM_STEP)
            KeyEvent.KEYCODE_DPAD_LEFT -> engine.previousSentence()
            KeyEvent.KEYCODE_DPAD_RIGHT -> engine.nextSentence()
            KeyEvent.KEYCODE_B -> vm.addBookmark()
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        // Reading stops when the screen does, and the place is kept.
        vm.engine.pause()
        vm.rememberPosition()
        super.onStop()
    }

    /**
     * "Open with Lectern", "Share to Lectern", and the launcher shortcuts for
     * recent books all land here.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.getBooleanExtra(EXTRA_HANDLED, false)) return
        intent.putExtra(EXTRA_HANDLED, true)

        intent.getStringExtra(EXTRA_BOOK_ID)?.let { bookId ->
            vm.requestOpen(bookId)
            return
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(vm::importDocument)

            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (uri != null) {
                    vm.importDocument(uri)
                } else {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::importPastedText)
                }
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "com.lectern.BOOK_ID"
        private const val EXTRA_HANDLED = "com.lectern.INTENT_HANDLED"
    }
}
