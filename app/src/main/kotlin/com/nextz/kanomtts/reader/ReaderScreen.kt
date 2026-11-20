package com.nextz.kanomtts.reader

import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.nextz.kanomtts.BuildConfig
import com.nextz.kanomtts.R
import com.nextz.kanomtts.feature.replacement.ReplacementRuleSheet
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenReplacements: (bookId: String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val quickAdd by viewModel.quickAddText.collectAsState()

    Box(
        modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is ReaderUiState.Loading -> CircularProgressIndicator()
            is ReaderUiState.Error -> Text(s.message, style = MaterialTheme.typography.titleMedium)
            is ReaderUiState.Ready -> {
                // Fragment layer (bottom)
                ReaderContent(bookId = bookId, state = s, viewModel = viewModel)

                // Chrome + ToC sheet layer (top)
                ReaderChromeLayer(
                    state = s,
                    onBack = onBack,
                    viewModel = viewModel,
                    onOpenReplacements = { onOpenReplacements(bookId) },
                )
            }
        }
    }

    // Quick-add replacement sheet: shown when user taps Add-Replacement on a selection.
    quickAdd?.let { selected ->
        QuickAddReplacementSheet(
            selected = selected,
            onSave = { p, r, rx, toThisBook, show -> viewModel.addReplacement(p, r, rx, toThisBook, show) },
            onDismiss = { viewModel.dismissQuickAdd() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddReplacementSheet(
    selected: String,
    onSave: (pattern: String, replacement: String, isRegex: Boolean, toThisBook: Boolean, showInReader: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var toThisBook by remember { mutableStateOf(false) }
    ReplacementRuleSheet(
        initial = null,
        prefilledPattern = selected,
        onSave = { p, r, rx, show -> onSave(p, r, rx, toThisBook, show) },
        onDismiss = onDismiss,
        scopeContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                FilterChip(
                    selected = !toThisBook,
                    onClick = { toThisBook = false },
                    label = { Text(stringResource(R.string.rule_scope_global)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = toThisBook,
                    onClick = { toThisBook = true },
                    label = { Text(stringResource(R.string.rule_scope_book)) },
                )
            }
        },
    )
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderChromeLayer(
    state: ReaderUiState.Ready,
    onBack: () -> Unit,
    viewModel: ReaderViewModel,
    onOpenReplacements: () -> Unit = {},
) {
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    // DEBUG-ONLY: tracks whether the sentence-highlight probe is currently active.
    var probeOn by rememberSaveable { mutableStateOf(false) }

    val ttsPlaying by viewModel.ttsPlaying.collectAsState()

    ReaderChrome(
        title = state.book.title,
        chromeVisible = state.chromeVisible,
        onBack = onBack,
        onTocOpen = { tocOpen = true },
        onSettingsOpen = { settingsOpen = true },
        onBookmarksOpen = { bookmarksOpen = true },
        ttsPlaying = ttsPlaying,
        onToggleTts = viewModel::toggleTts,
        // Wire debug probe only in DEBUG builds; null in release so the icon is never shown.
        onToggleProbe = if (BuildConfig.DEBUG) {
            {
                if (probeOn) {
                    viewModel.clearSentenceProbe()
                } else {
                    viewModel.runSentenceProbe()
                }
                probeOn = !probeOn
            }
        } else null,
    )

    if (tocOpen) {
        ReaderTocSheet(
            toc = state.toc,
            currentLocator = state.currentLocator,
            onNavigate = { link ->
                viewModel.goTo(link)
            },
            onDismiss = { tocOpen = false },
        )
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            viewModel = viewModel,
            onDismiss = { settingsOpen = false },
            onOpenReplacements = onOpenReplacements,
        )
    }

    if (bookmarksOpen) {
        ReaderBookmarksSheet(
            bookmarks = state.bookmarks,
            onAddCurrent = viewModel::addBookmarkAtCurrent,
            onNavigate = { locatorJson -> viewModel.goToBookmark(locatorJson) },
            onRemove = { id -> viewModel.removeBookmark(id) },
            onDismiss = { bookmarksOpen = false },
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderContent(bookId: String, state: ReaderUiState.Ready, viewModel: ReaderViewModel) {
    val activity = LocalActivity.current as FragmentActivity
    val containerId = rememberSaveable { View.generateViewId() }
    val tag = "reader-$bookId"

    // Remove the fragment when this composable leaves composition (back-press, nav pop, etc.)
    // so that a re-entry always commits a fresh fragment into the new FragmentContainerView.
    // commitNowAllowingStateLoss is synchronous (no re-open race) and safe even when state is
    // saved (config change / process backgrounding).
    DisposableEffect(activity, tag) {
        onDispose {
            val fm = activity.supportFragmentManager
            fm.findFragmentByTag(tag)?.let { frag ->
                (frag as? EpubNavigatorFragment)?.let { viewModel.unbindNavigator(it) }
                fm.beginTransaction().remove(frag).commitNowAllowingStateLoss()
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize().padding(
            top = state.prefs.topMarginDp.dp,
            bottom = state.prefs.bottomMarginDp.dp,
        ),
        factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
        update = { container ->
            // View.post queues until the container is attached to the window, so the commit is
            // safe pre-attach. commitNow() executes synchronously, so the fragment is immediately
            // retrievable and the findFragmentByTag guard + commit are atomic on the main
            // thread — this closes the latent double-commit race from Task 2.
            container.post {
                val fm = activity.supportFragmentManager
                if (fm.findFragmentByTag(tag) == null) {
                    fm.fragmentFactory = state.fragmentFactory
                    fm.beginTransaction()
                        .add(container.id, EpubNavigatorFragment::class.java, Bundle(), tag)
                        .commitNow()
                }
                // Hand the navigator to the VM so it can collect currentLocator.
                // Hard cast: the fragment was just committed under this unique tag via
                // commitNow, so it is guaranteed present and of this type — fail loudly if not.
                val nav = fm.findFragmentByTag(tag) as EpubNavigatorFragment
                viewModel.bindNavigator(nav)
            }
        },
    )
}
