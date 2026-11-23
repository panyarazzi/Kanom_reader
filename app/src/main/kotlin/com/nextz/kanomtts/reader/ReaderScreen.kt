package com.nextz.kanomtts.reader

import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.fragment.app.FragmentContainerView
import com.nextz.kanomtts.BuildConfig
import com.nextz.kanomtts.MainActivity
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
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activity, lifecycleOwner, viewModel) {
        val host = activity as? MainActivity
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.setReaderVisibleForStatistics(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.setReaderVisibleForStatistics(false)
                else -> Unit
            }
        }
        host?.setReaderKeyHandler(viewModel::onHardwareKeyEvent)
        lifecycle?.addObserver(observer)
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
            viewModel.setReaderVisibleForStatistics(true)
        }
        onDispose {
            lifecycle?.removeObserver(observer)
            viewModel.setReaderVisibleForStatistics(false)
            host?.setReaderKeyHandler(null)
        }
    }

    val state by viewModel.uiState.collectAsState()
    val relinkViewModel: com.nextz.kanomtts.feature.library.RelinkViewModel = hiltViewModel()
    val relinkState by relinkViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val relinkFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            relinkViewModel.searchFile(bookId, uri)
        }
    }
    val relinkFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            relinkViewModel.searchFolder(bookId, uri)
        }
    }
    val quickAdd by viewModel.quickAddText.collectAsState()
    val footnoteBubble by viewModel.footnoteBubble.collectAsState()
    var measuredTopProgressHeight by remember { mutableStateOf(0.dp) }
    var measuredFooterProgressHeight by remember { mutableStateOf(0.dp) }

    Box(
        modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is ReaderUiState.Loading -> CircularProgressIndicator()
            is ReaderUiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.message, style = MaterialTheme.typography.titleMedium)
                Text("หากย้ายไฟล์ EPUB ไปแล้ว สามารถเลือกไฟล์เดิมหรือค้นหาในโฟลเดอร์เพื่อเชื่อมกลับได้",
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { relinkFilePicker.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                    enabled = !relinkState.busy,
                ) {
                    Text("เลือกไฟล์ EPUB")
                }
                OutlinedButton(onClick = { relinkFolderPicker.launch(null) }, enabled = !relinkState.busy) {
                    Text("ค้นหาในโฟลเดอร์")
                }
                if (relinkState.busy) CircularProgressIndicator()
                relinkState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            is ReaderUiState.Ready -> {
                // Fragment layer (bottom)
                ReaderContent(
                    bookId = bookId,
                    state = s,
                    viewModel = viewModel,
                    measuredTopProgressHeight = measuredTopProgressHeight,
                    measuredFooterProgressHeight = measuredFooterProgressHeight,
                )

                // Chrome + ToC sheet layer (top)
                ReaderChromeLayer(
                    state = s,
                    onBack = onBack,
                    viewModel = viewModel,
                    onOpenReplacements = { onOpenReplacements(bookId) },
                    onTopProgressHeightChanged = { measuredTopProgressHeight = it },
                    onFooterProgressHeightChanged = { measuredFooterProgressHeight = it },
                )
                footnoteBubble?.let { bubble ->
                    ReaderFootnoteBalloon(bubble = bubble, onDismiss = viewModel::dismissFootnoteBubble)
                }
            }
        }
    }

    if (state is ReaderUiState.Error && relinkState.candidates.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!relinkState.busy) relinkViewModel.clearResults() },
            title = { Text("เลือกไฟล์ที่ตรงกับหนังสือเดิม") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    relinkState.candidates.forEach { candidate ->
                        val sizeLabel = candidate.size.takeIf { it > 0L }?.let { "$it bytes" } ?: "ไม่ทราบขนาด"
                        TextButton(
                            onClick = { relinkViewModel.confirm(candidate) { viewModel.retryLoad() } },
                            enabled = !relinkState.busy,
                        ) {
                            Column {
                                Text("${candidate.name} · $sizeLabel")
                                Text(candidate.sourceFolder.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { relinkViewModel.clearResults() }, enabled = !relinkState.busy) { Text("ยกเลิก") } },
        )
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
    onTopProgressHeightChanged: (Dp) -> Unit = {},
    onFooterProgressHeightChanged: (Dp) -> Unit = {},
) {
    var tocOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(false) }
    // DEBUG-ONLY: tracks whether the sentence-highlight probe is currently active.
    var probeOn by rememberSaveable { mutableStateOf(false) }

    val ttsPlaying by viewModel.ttsPlaying.collectAsState()
    // Keep chapter title and progression paired with the same Readium pagination event as page counts.
    val progressLocator = state.chapterProgressLocator ?: state.currentLocator
    val progressContent = buildProgressContent(
        pageNumber = state.chapterPageNumber,
        totalPages = state.chapterPageCount,
        chapterTitle = resolveProgressChapterTitle(
            paginationLocatorHref = state.chapterProgressLocator?.href?.toString(),
            paginationLocatorTitle = state.chapterProgressLocator?.title,
            currentLocatorHref = state.currentLocator?.href?.toString(),
            currentLocatorTitle = state.currentLocator?.title,
            toc = state.toc,
        ),
        bookTitle = state.book.title,
        totalProgression = progressLocator?.locations?.totalProgression,
        time = null,
        batteryPercent = null,
        charging = false,
    )

    ReaderChrome(
        title = state.book.title,
        chromeVisible = state.chromeVisible,
        progressContent = progressContent,
        topProgressLayout = ProgressLayout(state.prefs.topProgressLeft, state.prefs.topProgressCenter, state.prefs.topProgressRight),
        footerProgressLayout = ProgressLayout(state.prefs.footerProgressLeft, state.prefs.footerProgressCenter, state.prefs.footerProgressRight),
        progressFontSizeSp = state.prefs.progressFontSizeSp,
        topProgressEnabled = state.prefs.topProgressEnabled,
        footerProgressEnabled = state.prefs.footerProgressEnabled,
        onBack = onBack,
        onTocOpen = { tocOpen = true },
        onSettingsOpen = { settingsOpen = true },
        onBookmarksOpen = { bookmarksOpen = true },
        onFooterTap = { tocOpen = true },
        onFooterDoubleTap = { settingsOpen = true },
        onTopProgressHeightChanged = onTopProgressHeightChanged,
        onFooterProgressHeightChanged = onFooterProgressHeightChanged,
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
private fun ReaderContent(
    bookId: String,
    state: ReaderUiState.Ready,
    viewModel: ReaderViewModel,
    measuredTopProgressHeight: Dp,
    measuredFooterProgressHeight: Dp,
) {
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

    val hasProgress = state.prefs.topProgressEnabled || state.prefs.footerProgressEnabled
    val progressTopSafeSpace = if (hasProgress && state.prefs.topProgressEnabled) measuredTopProgressHeight + 8.dp else 0.dp
    val progressFooterSafeSpace = if (hasProgress && state.prefs.footerProgressEnabled) measuredFooterProgressHeight + 8.dp else 0.dp

    var pinchFontSize by remember { mutableStateOf(state.prefs.fontSizeSp) }
    var showPinchFontSize by remember { mutableStateOf(false) }
    LaunchedEffect(state.prefs.fontSizeSp) { pinchFontSize = state.prefs.fontSizeSp }
    LaunchedEffect(showPinchFontSize, pinchFontSize) {
        if (showPinchFontSize) { delay(1400L); showPinchFontSize = false }
    }
    Box(
        Modifier.fillMaxSize().padding(
            top = state.prefs.topMarginDp.dp + progressTopSafeSpace,
            bottom = state.prefs.bottomMarginDp.dp + progressFooterSafeSpace,
        ).pointerInput(Unit) {
            var accumulatedScale = 1f
            var gestureSize = pinchFontSize
            awaitEachGesture {
                var previousSpan: Float? = null
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val dx = pressed[0].position.x - pressed[1].position.x
                        val dy = pressed[0].position.y - pressed[1].position.y
                        val span = sqrt(dx * dx + dy * dy)
                        val previous = previousSpan
                        if (previous != null && previous > 0f) {
                            val zoomChange = span / previous
                            if (zoomChange != 1f) {
                                showPinchFontSize = true
                                if (accumulatedScale == 1f) gestureSize = pinchFontSize
                                accumulatedScale *= zoomChange
                                while (accumulatedScale >= 1.08f && gestureSize < 72) {
                                    gestureSize += 1
                                    accumulatedScale /= 1.08f
                                    pinchFontSize = gestureSize
                                    viewModel.onFontSize(gestureSize)
                                }
                                while (accumulatedScale <= 0.925f && gestureSize > 12) {
                                    gestureSize -= 1
                                    accumulatedScale /= 0.925f
                                    pinchFontSize = gestureSize
                                    viewModel.onFontSize(gestureSize)
                                }
                            }
                        }
                        previousSpan = span
                        pressed.forEach { it.consume() }
                    } else {
                        previousSpan = null
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
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
    if (showPinchFontSize) {
        Text(
            text = "${pinchFontSize} sp",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    }
}
