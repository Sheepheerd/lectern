package com.lectern.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.lectern.ui.library.LibraryScreen
import com.lectern.ui.reader.ReaderScreen
import com.lectern.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object LibraryRoute

@Serializable
data class ReaderRoute(val bookId: String)

@Serializable
object SettingsRoute

/**
 * Three destinations on one back stack: the shelf, a book, and settings. The
 * reader is addressed by book id, so the system restores whatever was open
 * after the process is killed.
 */
@Composable
fun LecternNavHost(vm: LecternViewModel, navController: NavHostController) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    // A freshly imported book, or one asked for by a launcher shortcut, opens
    // itself — after the graph is in place, never during composition.
    val pendingOpen by vm.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        pendingOpen?.let { bookId ->
            navController.navigate(ReaderRoute(bookId))
            vm.openRequestHandled()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box {
            NavHost(navController = navController, startDestination = LibraryRoute) {
                composable<LibraryRoute> {
                    LibraryScreen(
                        vm = vm,
                        onOpenBook = { navController.navigate(ReaderRoute(it.id)) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable<ReaderRoute> { entry ->
                    ReaderScreen(
                        vm = vm,
                        bookId = entry.toRoute<ReaderRoute>().bookId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
