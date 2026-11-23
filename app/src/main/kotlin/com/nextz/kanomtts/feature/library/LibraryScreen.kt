package com.nextz.kanomtts.feature.library

import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.nextz.kanomtts.ui.theme.KanomTheme

// ─── VM-bound entry point ───────────────────────────────────────────────────────

/**
 * Hilt-bound library screen. Includes a "+" FAB that launches the EPUB file picker
 * and shows import-result snackbars. Delegates rendering to [LibraryContent].
 */
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LibraryViewModel = hiltViewModel(),
    importViewModel: ImportViewModel,
    onOpenStatistics: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val statisticsViewModel: ReadingStatisticsViewModel = hiltViewModel()
    val statisticsState by statisticsViewModel.uiState.collectAsState()
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Content providers that don't support persistable permissions are fine —
                // we still attempt import with the transient permission we have.
            }
            importViewModel.importFrom(uri)
        }
    }

    LaunchedEffect(Unit) {
        importViewModel.events.collect { result ->
            val message = when (result) {
                is ImportResult.Imported -> "เพิ่มหนังสือแล้ว"
                is ImportResult.Relinked -> "เชื่อมไฟล์หนังสือเดิมแล้ว — ตำแหน่งอ่านและ Bookmark ยังคงอยู่"
                is ImportResult.Duplicate -> "มีหนังสือนี้อยู่แล้ว"
                is ImportResult.Unreadable -> "เปิดไฟล์ไม่ได้"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { launcher.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                    // This screen's Scaffold is nested inside KanomApp's Scaffold, whose NavHost is NOT
                    // inset by the bottom NavigationBar. The list content re-applies that inset via
                    // combinedPadding, but the FAB lives in the Scaffold slot outside it — so without this
                    // padding the FAB renders behind the bottom nav bar and is invisible/unclickable.
                    modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "เพิ่มหนังสือ")
                }
            }
        },
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
            onSortMode = viewModel::setSortMode,
            onShowFilename = viewModel::setShowFilename,
            onImportFolder = { folderLauncher.launch(null) },
            statistics = statisticsState,
            onOpenStatistics = onOpenStatistics,
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
    onSortMode: (LibrarySortMode) -> Unit = {},
    onShowFilename: (Boolean) -> Unit = {},
    onImportFolder: () -> Unit = {},
    statistics: ReadingStatisticsUiState? = null,
    onOpenStatistics: () -> Unit = {},
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
                if (statistics != null) {
                    ReadingStatisticsCard(state = statistics, onMore = onOpenStatistics)
                }
            }
        }

        if (!state.isSelectionMode && state.folders.isNotEmpty()) {
            item(key = "library-folders") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Card(Modifier.width(112.dp).clickable { onFolderFilter(null) }) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ทั้งหมด", style = MaterialTheme.typography.titleSmall)
                            Text("${state.totalBookCount} เล่ม", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    state.folders.forEach { folder ->
                        Card(Modifier.width(160.dp).clickable {
                            onFolderFilter(if (state.selectedFolder == folder.name) null else folder.name)
                        }) {
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
            state.continueCard?.let { continueCard ->
                item(key = "library-continue-reading") {
                    ContinueHero(card = continueCard, onClick = { onOpenBook(continueCard.id) })
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
                        text = if (state.query.isBlank()) "ยังไม่มีหนังสือ — แตะ + เพื่อเพิ่ม" else "ไม่พบหนังสือที่ค้นหา",
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
