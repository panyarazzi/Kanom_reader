package com.nextz.kanomtts.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.playback.ui.MiniPlayerBar
import com.nextz.kanomtts.playback.ui.MiniPlayerUiState
import com.nextz.kanomtts.playback.ui.PlaybackViewModel
import com.nextz.kanomtts.ui.theme.KanomTheme

// ─── VM-bound entry point ───────────────────────────────────────────────────────

/**
 * Folder Library test mode: imports recursively from a selected folder and shows
 * import-result snackbars. Single-file Classic Library entry is intentionally not
 * exposed in this test version; it remains a separate release-mode requirement.
 */
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LibraryViewModel = hiltViewModel(),
    importViewModel: ImportViewModel,
    onOpenStatistics: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val statisticsViewModel: ReadingStatisticsViewModel = hiltViewModel()
    val statisticsState by statisticsViewModel.uiState.collectAsState()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val playbackState by playbackViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            importViewModel.importFolder(uri)
        }
    }

    LaunchedEffect(Unit) {
        importViewModel.events.collect { result ->
            val message = when (result) {
                is ImportResult.Imported -> "เพิ่มหนังสือแล้ว"
                is ImportResult.Relinked -> "เชื่อมไฟล์หนังสือเดิมแล้ว — ตำแหน่งอ่านและ Bookmark ยังคงอยู่"
                is ImportResult.Duplicate -> "มีหนังสือนี้อยู่แล้ว"
                is ImportResult.Unreadable -> "เปิดไฟล์ไม่ได้"
                is ImportResult.EmptyFolder -> "ไม่พบไฟล์ EPUB ในโฟลเดอร์ที่เลือก"
                is ImportResult.FolderSummary -> "ตรวจ ${result.scanned} ไฟล์: เพิ่ม ${result.imported}, ซ้ำ ${result.duplicates}, อ่านไม่ได้ ${result.unreadable}"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // Merge outer contentPadding (from app scaffold) with inner padding from this scaffold.
        val combinedPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + innerPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + innerPadding.calculateBottomPadding(),
            start = contentPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = contentPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        )
        LibraryContent(
            state = uiState,
            onQueryChange = viewModel::setQuery,
            onViewMode = viewModel::setViewMode,
            onOpenBook = onOpenBook,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = viewModel::deleteSelected,
            onTagFilter = viewModel::setTagFilter,
            onFolderFilter = viewModel::setFolderFilter,
            onShelfFilter = viewModel::setShelfFilter,
            onSortMode = viewModel::setSortMode,
            onShowFilename = viewModel::setShowFilename,
            onImportFolder = { folderLauncher.launch(null) },
            statistics = statisticsState,
            onOpenStatistics = onOpenStatistics,
            onOpenNowPlaying = onOpenNowPlaying,
            playbackState = playbackState,
            onPlaybackToggle = playbackViewModel::playPause,
            onPlaybackNext = playbackViewModel::next,
            onPlaybackPrevious = playbackViewModel::prev,
            onPlaybackSleep = { playbackViewModel.setSleep(15) },
            contentPadding = combinedPadding,
            modifier = modifier,
        )
    }
}

// ─── State-hoisted content (previewable, no Hilt) ───────────────────────────────

/**
 * Pure-state library content: search bar / selection bar, view-mode toggle, continue-hero, book list.
 * No ViewModel reference — safe to use in @Preview.
 */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onTagFilter: (String?) -> Unit = {},
    onFolderFilter: (String?) -> Unit = {},
    onShelfFilter: (LibraryShelf?) -> Unit = {},
    onSortMode: (LibrarySortMode) -> Unit = {},
    onShowFilename: (Boolean) -> Unit = {},
    onImportFolder: () -> Unit = {},
    statistics: ReadingStatisticsUiState? = null,
    onOpenStatistics: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
    playbackState: MiniPlayerUiState? = null,
    onPlaybackToggle: () -> Unit = {},
    onPlaybackNext: () -> Unit = {},
    onPlaybackPrevious: () -> Unit = {},
    onPlaybackSleep: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    var showConfirm by remember { mutableStateOf(false) }
    // Close a stale confirm dialog if selection is cleared externally (e.g. system back).
    LaunchedEffect(state.isSelectionMode) { if (!state.isSelectionMode) showConfirm = false }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val gridColumnCount = ((screenWidthDp - 32 + 12) / 152).coerceAtLeast(1)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "library-header") {
            Spacer(Modifier.height(8.dp))
            if (state.isSelectionMode) {
                SelectionBar(
                    count = state.selectedIds.size,
                    onClose = onClearSelection,
                    onDelete = { showConfirm = true },
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BrandHeader(modifier = Modifier.weight(1f))
                    Column(
                        modifier = Modifier.weight(1.45f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        LibrarySearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ViewModeToggle(mode = state.viewMode, onSelect = onViewMode)
                    }
                }
                TextButton(onClick = onImportFolder) { Text("นำเข้าหนังสือจากโฟลเดอร์") }
            }
        }

        if (!state.isSelectionMode) {
            state.continueCard?.let { continueCard ->
                item(key = "library-continue-reading") {
                    ContinueHero(card = continueCard, onClick = { onOpenBook(continueCard.id) })
                }
            }
            item(key = "library-shelves") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ชั้นหนังสือ", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    ShelfCard("กำลังอ่าน", state.readingCount, state.selectedShelf == LibraryShelf.READING) {
                        onShelfFilter(LibraryShelf.READING)
                    }
                    ShelfCard("รออ่าน", state.toReadCount, state.selectedShelf == LibraryShelf.TO_READ) {
                        onShelfFilter(LibraryShelf.TO_READ)
                    }
                    ShelfCard("ซีรีส์/ชุด", state.seriesCount, state.selectedShelf == LibraryShelf.SERIES) {
                        onShelfFilter(LibraryShelf.SERIES)
                    }
                    ShelfCard("อ่านจบแล้ว", state.finishedCount, state.selectedShelf == LibraryShelf.FINISHED) {
                        onShelfFilter(LibraryShelf.FINISHED)
                    }
                    }
                }
            }
            if (state.recentlyOpened.isNotEmpty()) {
                item(key = "library-from-your-reading") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("จากหนังสือที่คุณอ่าน", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.recentlyOpened.forEach { card ->
                                BookGridItem(
                                    card = card,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onClick = { onOpenBook(card.id) },
                                    onToggle = { onToggleSelection(card.id) },
                                    modifier = Modifier.width(116.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (state.newestBooks.isNotEmpty()) {
                item(key = "library-new-on-shelf") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ใหม่บนชั้นหนังสือ", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.newestBooks.forEach { card ->
                                BookGridItem(
                                    card = card,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onClick = { onOpenBook(card.id) },
                                    onToggle = { onToggleSelection(card.id) },
                                    modifier = Modifier.width(116.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (statistics != null) {
                item(key = "library-reading-statistics-and-listening") {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val listeningVisible = playbackState?.visible == true
                        if (listeningVisible && maxWidth >= 600.dp) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(2f)) {
                                    ReadingStatisticsCard(state = statistics, onMore = onOpenStatistics)
                                }
                                Box(Modifier.weight(1f)) {
                                    MiniPlayerBar(
                                        state = playbackState!!,
                                        onPlayPause = onPlaybackToggle,
                                        onNext = onPlaybackNext,
                                        onPrev = onPlaybackPrevious,
                                        onSleep = onPlaybackSleep,
                                        onExpand = onOpenNowPlaying,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReadingStatisticsCard(state = statistics, onMore = onOpenStatistics)
                                if (listeningVisible) {
                                    MiniPlayerBar(
                                        state = playbackState!!,
                                        onPlayPause = onPlaybackToggle,
                                        onNext = onPlaybackNext,
                                        onPrev = onPlaybackPrevious,
                                        onSleep = onPlaybackSleep,
                                        onExpand = onOpenNowPlaying,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "library-folders") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.selectedFolder == null) "โฟลเดอร์หนังสือ" else state.selectedFolder.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.selectedFolder != null) {
                            TextButton(onClick = {
                                onFolderFilter(state.selectedFolder.substringBeforeLast('/', "").ifBlank { null })
                            }) { Text("ย้อนกลับ") }
                        }
                    }
                    if (state.selectedFolder != null) {
                        Text(
                            state.selectedFolder.replace("/", "  ›  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Card(Modifier.width(112.dp).clickable { onFolderFilter(null) }) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("คลังทั้งหมด", style = MaterialTheme.typography.titleSmall)
                                Text("${state.totalBookCount} เล่ม", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        state.folders.forEach { folder ->
                            Card(Modifier.width(160.dp).clickable { onFolderFilter(folder.path) }) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        folder.sampleCovers.forEach { cover ->
                                            CoverImage(cover, Modifier.width(25.dp).height(34.dp))
                                        }
                                    }
                                    Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text("${folder.count} เล่ม", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

        }

        if (!state.isSelectionMode) {
            item(key = "library-sort") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LibrarySortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.sortMode == mode,
                            onClick = { onSortMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
            item(key = "library-filename-toggle") {
                FilterChip(
                    selected = state.showFilename,
                    onClick = { onShowFilename(!state.showFilename) },
                    label = { Text(if (state.showFilename) "แสดงชื่อไฟล์ต้นฉบับ" else "แสดงชื่อหนังสือ") },
                )
            }
            if (state.availableTags.isNotEmpty()) {
                item(key = "library-tags") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedTag == null,
                            onClick = { onTagFilter(null) },
                            label = { Text("ทั้งหมด") },
                        )
                        state.availableTags.forEach { tag ->
                            FilterChip(
                                selected = state.selectedTag == tag,
                                onClick = { onTagFilter(if (state.selectedTag == tag) null else tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }

        }

        if (state.books.isEmpty()) {
            item(key = "library-empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = when {
                            state.totalBookCount == 0 -> "ยังไม่มีหนังสือ — แตะนำเข้าหนังสือจากโฟลเดอร์เพื่อเพิ่ม"
                            state.query.isNotBlank() -> "ไม่พบหนังสือที่ค้นหา"
                            else -> "ไม่พบหนังสือตามตัวกรองที่เลือก"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            when (state.viewMode) {
                LibraryViewMode.GRID -> {
                    items(
                        items = state.books.chunked(gridColumnCount),
                        key = { row -> "grid-${row.first().id}" },
                    ) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { card ->
                                Box(Modifier.weight(1f)) {
                                    BookGridItem(
                                        card = card,
                                        isSelectionMode = state.isSelectionMode,
                                        isSelected = card.id in state.selectedIds,
                                        onClick = { onOpenBook(card.id) },
                                        onToggle = { onToggleSelection(card.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            repeat((gridColumnCount - row.size).coerceAtLeast(0)) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                LibraryViewMode.LIST, LibraryViewMode.DETAIL -> {
                    items(state.books, key = { it.id }) { card ->
                        BookListItem(
                            card = card,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = card.id in state.selectedIds,
                            onClick = { onOpenBook(card.id) },
                            onToggle = { onToggleSelection(card.id) },
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = { onDeleteSelected(); showConfirm = false }) { Text("ลบ") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("ยกเลิก") }
            },
            title = { Text("ลบ ${state.selectedIds.size} เล่ม?") },
            text = { Text("การลบนี้ย้อนกลับไม่ได้") },
        )
    }
}

// ─── Previews ───────────────────────────────────────────────────────────────────

@Composable
private fun ShelfCard(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(116.dp).clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("$count เล่ม", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val sampleBooks = listOf(
    BookCard(
        id = "1",
        title = "นิทานพื้นบ้านไทย",
        author = "ผู้แต่งไทย",
        coverPath = null,
        percent = 0.45f,
        isReading = true,
    ),
    BookCard(
        id = "2",
        title = "สามก๊ก",
        author = "โรแบร์ เดอ กรานด์",
        coverPath = null,
        percent = 0.0f,
        isReading = false,
    ),
    BookCard(
        id = "3",
        title = "อุ้มบุญ: เรื่องสั้น",
        author = null,
        coverPath = null,
        percent = 0.92f,
        isReading = false,
    ),
    BookCard(
        id = "4",
        title = "ข้าวนอกนา",
        author = "นักเขียนนิรนาม",
        coverPath = null,
        percent = 0.0f,
        isReading = false,
    ),
)

private val sampleContinueCard = sampleBooks.first()

@Preview(name = "Library — GRID", showBackground = true)
@Composable
private fun LibraryGridPreview() {
    KanomTheme {
        LibraryContent(
            state = LibraryUiState(
                books = sampleBooks,
                continueCard = sampleContinueCard,
                viewMode = LibraryViewMode.GRID,
                query = "",
            ),
            onQueryChange = {},
            onViewMode = {},
            onOpenBook = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelected = {},
        )
    }
}

@Preview(name = "Library — LIST", showBackground = true)
@Composable
private fun LibraryListPreview() {
    KanomTheme {
        LibraryContent(
            state = LibraryUiState(
                books = sampleBooks,
                continueCard = sampleContinueCard,
                viewMode = LibraryViewMode.LIST,
                query = "",
            ),
            onQueryChange = {},
            onViewMode = {},
            onOpenBook = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelected = {},
        )
    }
}

@Preview(name = "Library — Empty", showBackground = true)
@Composable
private fun LibraryEmptyPreview() {
    KanomTheme {
        LibraryContent(
            state = LibraryUiState(
                books = emptyList(),
                continueCard = null,
                viewMode = LibraryViewMode.GRID,
                query = "",
            ),
            onQueryChange = {},
            onViewMode = {},
            onOpenBook = {},
            onToggleSelection = {},
            onClearSelection = {},
            onDeleteSelected = {},
        )
    }
}
