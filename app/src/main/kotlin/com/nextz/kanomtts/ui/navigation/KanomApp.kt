package com.nextz.kanomtts.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nextz.kanomtts.feature.library.ImportViewModel
import com.nextz.kanomtts.feature.library.LibraryScreen
import com.nextz.kanomtts.feature.replacement.ReplacementEditorScreen
import com.nextz.kanomtts.playback.ui.MiniPlayer
import com.nextz.kanomtts.reader.ReaderScreen
import com.nextz.kanomtts.reader.ReaderViewModel
import com.nextz.kanomtts.ui.screens.NowPlayingScreen
import com.nextz.kanomtts.ui.screens.SettingsScreen

@Composable
fun KanomApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute?.startsWith("reader/") != true
    val onNowPlaying = currentRoute == KanomDestination.NOW_PLAYING.route

    Scaffold(
        bottomBar = {
            Column {
                // Mini-player gates its own visibility (renders nothing when idle). It appears
                // above the nav bar on tabbed screens and alone at the bottom on the reader.
                // Hidden on Now-Playing, where it would redundantly mirror the full player.
                if (!onNowPlaying) {
                    MiniPlayer(
                        onExpand = {
                            navController.navigate(KanomDestination.NOW_PLAYING.route) {
                                popUpTo(KanomDestination.LIBRARY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                if (showBottomBar) {
                    NavigationBar {
                        KanomDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(KanomDestination.LIBRARY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = KanomDestination.LIBRARY.route,
            modifier = Modifier,
        ) {
            composable(KanomDestination.LIBRARY.route) {
                val activity = LocalActivity.current as FragmentActivity
                val importVm: ImportViewModel = hiltViewModel(activity)
                LibraryScreen(
                    onOpenBook = { id -> navController.navigate("reader/$id") },
                    modifier = Modifier,
                    contentPadding = padding,
                    importViewModel = importVm,
                )
            }
            composable(KanomDestination.NOW_PLAYING.route) { NowPlayingScreen(Modifier, padding) }
            composable(KanomDestination.SETTINGS.route) {
                SettingsScreen(Modifier, padding, onOpenReplacements = { navController.navigate("replacements") })
            }
            composable("replacements") {
                ReplacementEditorScreen(onBack = { navController.popBackStack() }, contentPadding = padding)
            }
            composable("replacements/{bookId}") {
                ReplacementEditorScreen(onBack = { navController.popBackStack() }, contentPadding = padding)
            }
            composable("reader/{bookId}") { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val vm: ReaderViewModel = hiltViewModel()
                ReaderScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                    onOpenReplacements = { id -> navController.navigate("replacements/$id") },
                    viewModel = vm,
                    contentPadding = padding,
                )
            }
        }
    }
}
