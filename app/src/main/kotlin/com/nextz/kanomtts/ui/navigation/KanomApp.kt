package com.nextz.kanomtts.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nextz.kanomtts.feature.library.ImportViewModel
import com.nextz.kanomtts.feature.settings.FirstLaunchRecoveryStatus
import com.nextz.kanomtts.feature.settings.FirstLaunchRecoveryViewModel
import com.nextz.kanomtts.feature.library.LibraryScreen
import com.nextz.kanomtts.feature.library.BookAnnotationsScreen
import com.nextz.kanomtts.feature.library.ReadingStatisticsScreen
import com.nextz.kanomtts.feature.replacement.ReplacementEditorScreen
import com.nextz.kanomtts.playback.ui.MiniPlayer
import com.nextz.kanomtts.reader.ReaderScreen
import com.nextz.kanomtts.reader.ReaderViewModel
import com.nextz.kanomtts.ui.screens.NowPlayingScreen
import com.nextz.kanomtts.ui.screens.SettingsScreen
import com.nextz.kanomtts.ui.screens.AdvancedSettingsScreen

@Composable
fun KanomApp(
    recoveryViewModel: FirstLaunchRecoveryViewModel,
    onRequestStoragePermission: () -> Unit,
    onStartupFinished: () -> Unit,
) {
    val recoveryState by recoveryViewModel.uiState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(recoveryState.status) {
        if (recoveryState.status == FirstLaunchRecoveryStatus.READY) {
            onStartupFinished()
        }
    }
    if (recoveryState.status != com.nextz.kanomtts.feature.settings.FirstLaunchRecoveryStatus.READY) {
        FirstLaunchRecoveryScreen(
            state = recoveryState,
            onRequestPermission = onRequestStoragePermission,
            onRetry = recoveryViewModel::retryRestore,
            onContinueAfterFailure = recoveryViewModel::continueAfterFailure,
            onContinueWithoutPermission = recoveryViewModel::continueWithoutPermission,
        )
        return
    }
    val navController = rememberNavController()
    var showTtsSessionPopup by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute?.startsWith("reader/") != true && currentRoute?.startsWith("book_annotations") != true && currentRoute !in setOf("reading_statistics", "advanced_settings", "backup_restore")

    Scaffold(
        bottomBar = {
            Column {
                // Mini-player gates its own visibility (renders nothing when idle). It appears
                // above the nav bar on tabbed screens and alone at the bottom on the reader.
                if (currentRoute != KanomDestination.LIBRARY.route) {
                    MiniPlayer(
                        onExpand = { showTtsSessionPopup = true },
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
        Box(Modifier.fillMaxSize()) {
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
                    onOpenStatistics = { navController.navigate("reading_statistics") },
                    onOpenAnnotations = { id -> navController.navigate("book_annotations/$id") },
                    onOpenNowPlaying = { showTtsSessionPopup = true },
                )
            }
            composable("reading_statistics") {
                ReadingStatisticsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAnnotations = { navController.navigate("book_annotations") },
                    contentPadding = padding,
                )
            }
            composable("book_annotations/{bookId}") { entry ->
                BookAnnotationsScreen(
                    onBack = { navController.popBackStack() },
                    contentPadding = padding,
                    initialBookId = entry.arguments?.getString("bookId"),
                )
            }
            composable("book_annotations") {
                BookAnnotationsScreen(
                    onBack = { navController.popBackStack() },
                    contentPadding = padding,
                )
            }
            composable(KanomDestination.NOW_PLAYING.route) {
                NowPlayingScreen(
                    modifier = Modifier,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(KanomDestination.SETTINGS.route) {
                SettingsScreen(
                    modifier = Modifier,
                    contentPadding = padding,
                    onOpenReplacements = { navController.navigate("replacements") },
                    onOpenAdvanced = { navController.navigate("advanced_settings") },
                    onOpenBackupRestore = { navController.navigate("backup_restore") },
                )
            }
            composable("backup_restore") {
                SettingsScreen(
                    modifier = Modifier,
                    contentPadding = padding,
                    backupOnly = true,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("advanced_settings") {
                AdvancedSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReplacements = { navController.navigate("replacements") },
                    onOpenBackupRestore = { navController.navigate("backup_restore") },
                    onOpenAnnotations = { navController.navigate("book_annotations") },
                    contentPadding = padding,
                )
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
        if (showTtsSessionPopup) {
            NowPlayingScreen(onBack = { showTtsSessionPopup = false })
        }
        }
    }
}
