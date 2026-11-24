package com.nextz.kanomtts.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
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
import java.io.File

// ─── VM-bound entry point ───────────────────────────────────────────────────────

/**
 * Folder Library test mode: supports recursive folder import and individual EPUB import
 * through separate floating actions, while retaining the real source-folder hierarchy.
 */
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: LibraryViewModel = hiltViewModel(),
    importViewModel: ImportViewModel,
    onOpenStatistics: () -> Unit = {},
    onOpenAnnotations: (String) -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val statisticsViewModel: ReadingStatisticsViewModel = hiltViewModel()
    val statisticsState by statisticsViewModel.uiState.collectAsState()
    val playbackViewModel: PlaybackViewModel = hiltViewModel()
    val playbackState by playbackViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingCoverBookId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = uiState.isSelectionMode || uiState.selectedFolder != null) {
        if (uiState.isSelectionMode) {
            viewModel.clearSelection()
        } else {
            viewModel.setFolderFilter(uiState.selectedFolder?.substringBeforeLast('/', "")?.ifBlank { null })
        }
    }

    val singleBookLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            importViewModel.importFrom(uri)
        }
    }

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

    val coverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val bookId = pendingCoverBookId
        pendingCoverBookId = null
        if (uri != null && bookId != null) {
            runCatching {
                val directory = File(context.filesDir, "covers")
                check(directory.exists() || directory.mkdirs()) { "สร้างโฟลเดอร์ภาพปกไม่สำเร็จ" }
                val destination = File(directory, "custom-$bookId-${System.currentTimeMillis()}.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                } ?: error("อ่านภาพที่เลือกไม่ได้")
                viewModel.updateBookCover(bookId, destination.absolutePath)
            }.onFailure {
                android.widget.Toast.makeText(context, "เปลี่ยนภาพปกไม่สำเร็จ: ${it.message ?: "เกิดข้อผิดพลาด"}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        importViewModel.events.collect { result ->
            val message = when (result) {
                is ImportResult.Imported -> "เพิ่มหนังสือแล้ว"
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
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.libraryMode == LibraryPresentationMode.FOLDER) {
                    SmallFloatingActionButton(onClick = { folderLauncher.launch(null) }) {
                        androidx.compose.material3.Icon(Icons.Filled.CreateNewFolder, contentDescription = "นำเข้าโฟลเดอร์")
                    }
                }
                FloatingActionButton(onClick = { singleBookLauncher.launch(arrayOf("application/epub+zip")) }) {
                    androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = "นำเข้า EPUB")
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
            onLibraryMode = viewModel::setLibraryMode,
            onOpenBook = onOpenBook,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = viewModel::deleteSelected,
            onTagFilter = viewModel::setTagFilter,
            onFolderFilter = viewModel::setFolderFilter,
            onShelfFilter = viewModel::setShelfFilter,
            onGroupMode = viewModel::setGroupMode,
            onSortMode = viewModel::setSortMode,
            onSortDescending = viewModel::setSortDescending,
            onSecondarySortMode = viewModel::setSecondarySortMode,
            onShowFilename = viewModel::setShowFilename,
            onImportFolder = { folderLauncher.launch(null) },
            statistics = statisticsState,
            onOpenStatistics = onOpenStatistics,
            onOpenAnnotations = onOpenAnnotations,
            onSetReadingStatus = viewModel::setReadingStatus,
            onUpdateBookMetadata = viewModel::updateBookMetadata,
            onChangeBookCover = { bookId -> pendingCoverBookId = bookId; coverLauncher.launch("image/*") },
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onLibraryMode: (LibraryPresentationMode) -> Unit = {},
    onOpenBook: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onTagFilter: (String?) -> Unit = {},
    onFolderFilter: (String?) -> Unit = {},
    onShelfFilter: (LibraryShelf?) -> Unit = {},
    onGroupMode: (LibraryGroupMode) -> Unit = {},
    onSortMode: (LibrarySortMode) -> Unit = {},
    onSortDescending: (Boolean) -> Unit = {},
    onSecondarySortMode: (LibrarySecondarySortMode) -> Unit = {},
    onShowFilename: (Boolean) -> Unit = {},
    onImportFolder: () -> Unit = {},
    statistics: ReadingStatisticsUiState? = null,
    onOpenStatistics: () -> Unit = {},
    onOpenAnnotations: (String) -> Unit = {},
    onSetReadingStatus: (String, LibraryShelf) -> Unit = { _, _ -> },
    onUpdateBookMetadata: (String, String, String?, String?, Double?) -> Unit = { _, _, _, _, _ -> },
    onChangeBookCover: (String) -> Unit = {},
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
    var showContinueList by remember { mutableStateOf(false) }
    var contextBook by remember { mutableStateOf<BookCard?>(null) }
    var detailsBook by remember { mutableStateOf<BookCard?>(null) }
    var removeBook by remember { mutableStateOf<BookCard?>(null) }
    var shelfBook by remember { mutableStateOf<BookCard?>(null) }
    var metadataBook by remember { mutableStateOf<BookCard?>(null) }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataAuthor by remember { mutableStateOf("") }
    var metadataSeriesName by remember { mutableStateOf("") }
    var metadataSeriesPosition by remember { mutableStateOf("") }
    var showOrganizationControls by remember { mutableStateOf(false) }
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var secondarySortMenuExpanded by remember { mutableStateOf(false) }
    // Close a stale confirm dialog if selection is cleared externally (e.g. system back).
    LaunchedEffect(state.isSelectionMode) { if (!state.isSelectionMode) showConfirm = false }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val gridColumnCount = ((screenWidthDp - 32 + 12) / 152).coerceAtLeast(1)
    val groupedBookSections: List<Pair<String, List<BookCard>>> = when (state.groupMode) {
        LibraryGroupMode.NONE, LibraryGroupMode.FOLDER -> listOf("" to state.books)
        LibraryGroupMode.AUTHOR -> state.books.groupBy { it.author?.takeIf(String::isNotBlank) ?: "ไม่ทราบผู้แต่ง" }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
        LibraryGroupMode.SERIES -> state.books
            .groupBy { card -> card.seriesName?.takeIf(String::isNotBlank) ?: "ไม่มี Series Metadata" }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, books) -> name to books.sortedWith(compareBy<BookCard> { it.seriesPosition ?: Double.MAX_VALUE }.thenBy { it.title.lowercase() }) }
        LibraryGroupMode.READING_STATUS -> state.books.groupBy { card ->
            when (card.effectiveReadingShelf()) {
                LibraryShelf.READING -> "กำลังอ่าน"
                LibraryShelf.TO_READ -> "รออ่าน"
                LibraryShelf.FINISHED -> "อ่านจบแล้ว"
                LibraryShelf.SERIES -> "ซีรีส์/ชุด"
            }
        }.toList().sortedBy { listOf("กำลังอ่าน", "รออ่าน", "อ่านจบแล้ว").indexOf(it.first) }
        LibraryGroupMode.TAG -> {
            val tagged = state.books.flatMap { card ->
                if (card.tags.isEmpty()) listOf("ไม่มีแท็ก" to card) else card.tags.map { it to card }
            }
            tagged.groupBy({ it.first }, { it.second }).toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stickyHeader(key = "library-header") {
            Spacer(Modifier.height(8.dp))
            if (state.isSelectionMode) {
                SelectionBar(
                    count = state.selectedIds.size,
                    onClose = onClearSelection,
                    onDelete = { showConfirm = true },
                )
            } else {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val searchAndControls: @Composable () -> Unit = {
                        LibrarySearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ViewModeToggle(mode = state.viewMode, onSelect = onViewMode)
                            TextButton(onClick = { showOrganizationControls = !showOrganizationControls }) {
                                Text(if (showOrganizationControls) "ปิดตัวกรอง" else "จัดกลุ่ม/เรียง")
                            }
                        }
                        if (showOrganizationControls) {
                            OrganizationControlsPanel(
                                state, onLibraryMode, onGroupMode, onSortMode, onSortDescending,
                                onSecondarySortMode, onShowFilename,
                                groupMenuExpanded, { groupMenuExpanded = it },
                                sortMenuExpanded, { sortMenuExpanded = it },
                                secondarySortMenuExpanded, { secondarySortMenuExpanded = it },
                            )
                        }
                    }
                    if (maxWidth >= 560.dp) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                BrandHeader(modifier = Modifier.fillMaxWidth())
                            }
                            Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                searchAndControls()
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BrandHeader(modifier = Modifier.fillMaxWidth())
                            searchAndControls()
                        }
                    }
                }
            }
        }

        if (!state.isSelectionMode) {
            if (statistics != null) {
                stickyHeader(key = "library-reading-statistics-and-listening") {
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 4.dp),
                    ) {
                        val playerState = playbackState ?: MiniPlayerUiState(
                            visible = false,
                            isPlaying = false,
                            title = "ยังไม่มีหนังสือที่เปิดล่าสุด",
                            author = null,
                            coverPath = null,
                            voiceLabel = null,
                            sleepRemainingMs = null,
                        )
                        if (maxWidth >= 720.dp) {
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
                                        state = playerState,
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
                                MiniPlayerBar(
                                    state = playerState,
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
            item(key = "library-continue-and-shelves") {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val continueContent: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("อ่านต่อ", style = MaterialTheme.typography.titleMedium)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                (state.continueCards.ifEmpty { listOfNotNull(state.continueCard) }).take(4).forEach { card ->
                                    BookGridItem(
                                        card = card,
                                        isSelectionMode = false,
                                        isSelected = false,
                                        onClick = { onOpenBook(card.id) },
                                        onToggle = { onToggleSelection(card.id) },
                                        onLongPress = { if (state.isSelectionMode) onToggleSelection(card.id) else contextBook = card },
                                        modifier = Modifier.width(104.dp),
                                    )
                                }
                                if (state.continueCards.size > 4) {
                                    Card(
                                        modifier = Modifier.width(104.dp).clickable { showContinueList = true },
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                state.continueCards.take(4).forEach { card ->
                                                    CoverImage(card.coverPath, Modifier.weight(1f).height(46.dp))
                                                }
                                            }
                                            Text("อ่านต่อทั้งหมด", style = MaterialTheme.typography.labelMedium)
                                            Text("${state.continueCards.size} เล่ม", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val shelvesContent: @Composable () -> Unit = {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Shelves", style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShelfCard("กำลังอ่าน", state.readingCount, state.selectedShelf == LibraryShelf.READING,
                                        { onShelfFilter(LibraryShelf.READING) }, Modifier.weight(1f))
                                    ShelfCard("รออ่าน", state.toReadCount, state.selectedShelf == LibraryShelf.TO_READ,
                                        { onShelfFilter(LibraryShelf.TO_READ) }, Modifier.weight(1f))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ShelfCard("ซีรีส์ / ชุด", state.seriesCount, state.selectedShelf == LibraryShelf.SERIES,
                                        { onShelfFilter(LibraryShelf.SERIES) }, Modifier.weight(1f))
                                    ShelfCard("อ่านจบแล้ว", state.finishedCount, state.selectedShelf == LibraryShelf.FINISHED,
                                        { onShelfFilter(LibraryShelf.FINISHED) }, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    if (maxWidth >= 720.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(2f)) { continueContent() }
                            Box(Modifier.weight(1f)) { shelvesContent() }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            continueContent()
                            shelvesContent()
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
                                    onLongPress = { if (state.isSelectionMode) onToggleSelection(card.id) else contextBook = card },
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
                                    onLongPress = { if (state.isSelectionMode) onToggleSelection(card.id) else contextBook = card },
                                    modifier = Modifier.width(116.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (state.libraryMode == LibraryPresentationMode.FOLDER &&
                state.groupMode == LibraryGroupMode.FOLDER &&
                state.query.isBlank() && state.selectedTag == null && state.selectedShelf == null
            ) item(key = "library-folders") {
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
                    if (state.folders.isNotEmpty()) {
                        when (state.viewMode) {
                            LibraryViewMode.GRID -> {
                                val folderColumns = if (screenWidthDp >= 560) 4 else 2
                                state.folders.chunked(folderColumns).forEach { folderRow ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        folderRow.forEach { folder ->
                                            Box(Modifier.weight(1f)) {
                                                FolderGridItem(
                                                    folder = folder,
                                                    onClick = { onFolderFilter(folder.path) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                            }
                                        }
                                        repeat((folderColumns - folderRow.size).coerceAtLeast(0)) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            LibraryViewMode.LIST, LibraryViewMode.DETAIL -> {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    state.folders.forEach { folder ->
                                        FolderListItem(
                                            folder = folder,
                                            onClick = { onFolderFilter(folder.path) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        if (!state.isSelectionMode) {
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

        if (state.books.isEmpty() && state.folders.isEmpty()) {
            item(key = "library-empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = when {
                            state.totalBookCount == 0 -> "ยังไม่มีหนังสือ — ใช้ปุ่ม + เพื่อนำเข้า EPUB หรือปุ่มโฟลเดอร์เพื่อนำเข้าทั้งโฟลเดอร์"
                            state.query.isNotBlank() -> "ไม่พบหนังสือที่ค้นหา"
                            else -> "ไม่พบหนังสือตามตัวกรองที่เลือก"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            groupedBookSections.forEachIndexed { groupIndex, (groupTitle, groupBooks) ->
                if (groupTitle.isNotBlank()) {
                    item(key = "library-group-header-$groupIndex") {
                        Text(groupTitle, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                    }
                }
                when (state.viewMode) {
                    LibraryViewMode.GRID -> {
                        items(
                            items = groupBooks.chunked(gridColumnCount),
                            key = { row -> "grid-$groupIndex-${row.first().id}" },
                        ) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { card ->
                                    Box(Modifier.weight(1f)) {
                                        BookGridItem(
                                            card = card,
                                            isSelectionMode = state.isSelectionMode,
                                            isSelected = card.id in state.selectedIds,
                                            onClick = { onOpenBook(card.id) },
                                            onToggle = { onToggleSelection(card.id) },
                                    onLongPress = { if (state.isSelectionMode) onToggleSelection(card.id) else contextBook = card },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                repeat((gridColumnCount - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    LibraryViewMode.LIST, LibraryViewMode.DETAIL -> {
                        items(groupBooks, key = { "list-$groupIndex-${it.id}" }) { card ->
                            BookListItem(
                                card = card,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = card.id in state.selectedIds,
                                onClick = { onOpenBook(card.id) },
                                onToggle = { onToggleSelection(card.id) },
                                    onLongPress = { if (state.isSelectionMode) onToggleSelection(card.id) else contextBook = card },
                            )
                        }
                    }
                }
            }
        }


    }

    if (showContinueList) {
        AlertDialog(
            onDismissRequest = { showContinueList = false },
            title = { Text("อ่านต่อทั้งหมด (${state.continueCards.size} เล่ม)") },
            text = {
                Column(
                    Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.continueCards.forEach { card ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                showContinueList = false
                                onOpenBook(card.id)
                            }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverImage(card.coverPath, Modifier.width(42.dp).height(58.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(card.title, style = MaterialTheme.typography.bodyMedium)
                                card.author?.takeIf { it.isNotBlank() }?.let {
                                    Text("ผู้แต่ง: $it", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${(card.percent * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContinueList = false }) { Text("Done") } },
        )
    }

    contextBook?.let { book ->
        AlertDialog(
            onDismissRequest = { contextBook = null },
            title = { Text(book.displayTitle, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onOpenAnnotations(book.id); contextBook = null }) { Text("🏷 Tags / Notes") }
                    TextButton(onClick = { detailsBook = book; contextBook = null }) { Text("รายละเอียดหนังสือ") }
                    TextButton(onClick = { onSetReadingStatus(book.id, LibraryShelf.FINISHED); contextBook = null }) { Text("ทำเครื่องหมายว่าอ่านจบแล้ว") }
                    TextButton(onClick = { onSetReadingStatus(book.id, LibraryShelf.TO_READ); contextBook = null }) { Text("ทำเครื่องหมายว่ายังไม่ได้อ่าน") }
                    TextButton(onClick = { shelfBook = book; contextBook = null }) { Text("ย้ายไปชั้นหนังสือ…") }
                    TextButton(onClick = { onChangeBookCover(book.id); contextBook = null }) { Text("เปลี่ยนภาพปก") }
                    TextButton(onClick = { metadataBook = book; metadataTitle = book.title; metadataAuthor = book.author.orEmpty(); metadataSeriesName = book.seriesName.orEmpty(); metadataSeriesPosition = book.seriesPosition?.toString().orEmpty(); contextBook = null }) { Text("แก้ไขข้อมูลหนังสือ") }
                    TextButton(onClick = { onToggleSelection(book.id); contextBook = null }) { Text("เลือกหลายรายการ") }
                    TextButton(onClick = { removeBook = book; contextBook = null }) { Text("นำออกจากรายการ") }
                }
            },
            confirmButton = {},
        )
    }
    detailsBook?.let { book ->
        AlertDialog(
            onDismissRequest = { detailsBook = null },
            title = { Text("รายละเอียดหนังสือ") },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    book.author?.takeIf { it.isNotBlank() }?.let { Text("ผู้แต่ง: $it") }
                    book.description?.let(::plainBookDescription)?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    book.filename.takeIf { it.isNotBlank() }?.let { Text("ชื่อไฟล์: $it") }
                    book.sourceFolder?.takeIf { it.isNotBlank() }?.let { Text("โฟลเดอร์: $it") }
                    book.seriesName?.takeIf { it.isNotBlank() }?.let { Text("ซีรีส์: $it") }
                    if (book.tags.isNotEmpty()) Text("Tags: ${book.tags.joinToString(" · ")}")
                    Text("ความคืบหน้า: ${(book.percent * 100).toInt()}%")
                    Text("สถานะ: " + when (book.effectiveReadingShelf()) { LibraryShelf.READING -> "กำลังอ่าน"; LibraryShelf.TO_READ -> "รออ่าน"; LibraryShelf.FINISHED -> "อ่านจบแล้ว"; LibraryShelf.SERIES -> "ซีรีส์/ชุด" })
                }
            },
            confirmButton = { TextButton(onClick = { detailsBook = null }) { Text("ปิด") } },
        )
    }
    shelfBook?.let { book ->
        AlertDialog(
            onDismissRequest = { shelfBook = null },
            title = { Text("เลือกชั้นหนังสือ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onSetReadingStatus(book.id, LibraryShelf.READING); shelfBook = null }) { Text("กำลังอ่าน") }
                    TextButton(onClick = { onSetReadingStatus(book.id, LibraryShelf.TO_READ); shelfBook = null }) { Text("รออ่าน") }
                    TextButton(onClick = { onSetReadingStatus(book.id, LibraryShelf.FINISHED); shelfBook = null }) { Text("อ่านจบแล้ว") }
                }
            },
            confirmButton = { TextButton(onClick = { shelfBook = null }) { Text("ปิด") } },
        )
    }
    metadataBook?.let { book ->
        AlertDialog(
            onDismissRequest = { metadataBook = null },
            title = { Text("แก้ไขข้อมูลหนังสือ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = metadataTitle, onValueChange = { metadataTitle = it }, label = { Text("ชื่อหนังสือ") }, singleLine = true)
                    OutlinedTextField(value = metadataAuthor, onValueChange = { metadataAuthor = it }, label = { Text("ผู้แต่ง") }, singleLine = true)
                    OutlinedTextField(value = metadataSeriesName, onValueChange = { metadataSeriesName = it }, label = { Text("ชื่อซีรีส์ / ชุดหนังสือ") }, singleLine = true)
                    OutlinedTextField(value = metadataSeriesPosition, onValueChange = { metadataSeriesPosition = it }, label = { Text("ลำดับในซีรีส์") }, singleLine = true)
                    Text("แก้ไขเฉพาะข้อมูลในคลัง Kanom Reader ไม่เปลี่ยนชื่อไฟล์ EPUB ต้นฉบับ")
                }
            },
            confirmButton = {
                TextButton(enabled = metadataTitle.isNotBlank() && (metadataSeriesPosition.isBlank() || metadataSeriesPosition.toDoubleOrNull()?.isFinite() == true), onClick = { onUpdateBookMetadata(book.id, metadataTitle, metadataAuthor, metadataSeriesName, metadataSeriesPosition.toDoubleOrNull()); metadataBook = null }) { Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick = { metadataBook = null }) { Text("ยกเลิก") } },
        )
    }
    removeBook?.let { book ->
        AlertDialog(
            onDismissRequest = { removeBook = null },
            title = { Text("นำหนังสือ ${book.displayTitle} ออกจากรายการใช่หรือไม่?") },
            text = { Text("การดำเนินการนี้จะนำข้อมูลหนังสือออกจาก Library เท่านั้น ไม่ได้ลบไฟล์ EPUB ต้นฉบับที่จัดเก็บอยู่ในเครื่อง") },
            confirmButton = {
                TextButton(onClick = { onToggleSelection(book.id); onDeleteSelected(); removeBook = null }) { Text("นำออก") }
            },
            dismissButton = { TextButton(onClick = { removeBook = null }) { Text("ยกเลิก") } },
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = { onDeleteSelected(); showConfirm = false }) { Text("นำออก") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("ยกเลิก") }
            },
            title = { Text("นำหนังสือ ${state.selectedIds.size} เล่มออกจากรายการใช่หรือไม่?") },
            text = { Text("การดำเนินการนี้จะนำข้อมูลหนังสือออกจาก Library เท่านั้น ไม่ได้ลบไฟล์ EPUB ต้นฉบับที่จัดเก็บอยู่ในเครื่อง") },
        )
    }
}

@Composable
private fun OrganizationControlsPanel(
    state: LibraryUiState,
    onLibraryMode: (LibraryPresentationMode) -> Unit,
    onGroupMode: (LibraryGroupMode) -> Unit,
    onSortMode: (LibrarySortMode) -> Unit,
    onSortDescending: (Boolean) -> Unit,
    onSecondarySortMode: (LibrarySecondarySortMode) -> Unit,
    onShowFilename: (Boolean) -> Unit,
    groupMenuExpanded: Boolean,
    setGroupMenuExpanded: (Boolean) -> Unit,
    sortMenuExpanded: Boolean,
    setSortMenuExpanded: (Boolean) -> Unit,
    secondarySortMenuExpanded: Boolean,
    setSecondarySortMenuExpanded: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.libraryMode == LibraryPresentationMode.CLASSIC, onClick = { onLibraryMode(LibraryPresentationMode.CLASSIC) }, label = { Text("Classic") })
                FilterChip(selected = state.libraryMode == LibraryPresentationMode.FOLDER, onClick = { onLibraryMode(LibraryPresentationMode.FOLDER) }, label = { Text("Folder") })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Group by", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Box {
                    TextButton(onClick = { setGroupMenuExpanded(true) }) { Text(state.groupMode.label) }
                    DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { setGroupMenuExpanded(false) }) {
                        LibraryGroupMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                                onGroupMode(mode); setGroupMenuExpanded(false)
                            })
                        }
                    }
                }
            }
            if (state.groupMode == LibraryGroupMode.SERIES) {
                Text("จัดกลุ่มจาก Series Metadata ที่ฝังอยู่ใน EPUB เท่านั้น",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Sort by", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Box {
                    TextButton(onClick = { setSortMenuExpanded(true) }) {
                        Text("${state.sortMode.label} · ${if (state.sortDescending) state.sortMode.descendingLabel else state.sortMode.ascendingLabel}")
                    }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { setSortMenuExpanded(false) }) {
                        LibrarySortMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                                onSortMode(mode); setSortMenuExpanded(false)
                            })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Then by", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Box {
                    TextButton(onClick = { setSecondarySortMenuExpanded(true) }) { Text(state.secondarySortMode.label) }
                    DropdownMenu(expanded = secondarySortMenuExpanded, onDismissRequest = { setSecondarySortMenuExpanded(false) }) {
                        LibrarySecondarySortMode.entries.filterNot { mode ->
                            when (state.sortMode) {
                                LibrarySortMode.TITLE_ASC -> mode.name.startsWith("TITLE_")
                                LibrarySortMode.AUTHOR_ASC -> mode.name.startsWith("AUTHOR_")
                                LibrarySortMode.FILENAME_ASC -> mode.name.startsWith("FILENAME_")
                                LibrarySortMode.ADDED_AT_ASC -> mode.name.startsWith("ADDED_")
                                LibrarySortMode.LAST_OPENED_AT_ASC -> mode.name.startsWith("OPENED_")
                                LibrarySortMode.PROGRESS_ASC -> false
                            }
                        }.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.label) }, onClick = {
                                onSecondarySortMode(mode); setSecondarySortMenuExpanded(false)
                            })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ทิศทาง", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { onSortDescending(!state.sortDescending) }) {
                    Text(if (state.sortDescending) state.sortMode.descendingLabel else state.sortMode.ascendingLabel)
                }
                TextButton(onClick = { onShowFilename(!state.showFilename) }) {
                    Text(if (state.showFilename) "ชื่อไฟล์" else "ชื่อหนังสือ")
                }
            }
        }
    }
}

@Composable
private fun FolderGridItem(
    folder: LibraryFolderCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                folder.sampleCovers.take(4).forEach { cover ->
                    CoverImage(cover, Modifier.weight(1f).height(42.dp))
                }
                if (folder.sampleCovers.isEmpty()) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "โฟลเดอร์",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                    )
                }
            }
            Text(
                folder.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                minLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${folder.count} เล่ม",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderListItem(
    folder: LibraryFolderCard,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.Folder,
            contentDescription = "โฟลเดอร์",
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${folder.count} เล่ม",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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


private fun plainBookDescription(source: String): String =
    android.text.Html.fromHtml(source, android.text.Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace('\u00A0', ' ')
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
