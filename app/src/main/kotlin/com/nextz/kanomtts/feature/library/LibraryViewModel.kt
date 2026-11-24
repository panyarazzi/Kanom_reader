package com.nextz.kanomtts.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookTagDao
import com.nextz.kanomtts.data.db.BookTagEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.repository.BookDeleter
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.ProgressReader
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.playback.PlaybackStopper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

fun playingBookIsSelected(state: PlaybackState, selected: Set<String>): Boolean = when (state) {
    is PlaybackState.Playing -> state.bookId in selected
    is PlaybackState.Paused -> state.bookId in selected
    else -> false
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    bookReader: BookReader,
    progressReader: ProgressReader,
    private val bookDeleter: BookDeleter,
    private val playbackStopper: PlaybackStopper,
    private val bookTagDao: BookTagDao? = null,
    private val bookDao: BookDao? = null,
) : ViewModel() {

    private val _viewMode = MutableStateFlow(LibraryViewMode.GRID)
    private val _libraryMode = MutableStateFlow(LibraryMode.FOLDER)
    private val _query = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow(emptySet<String>())
    private val _selectedTag = MutableStateFlow<String?>(null)
    private val _selectedFolder = MutableStateFlow<String?>(null)
    private val _groupMode = MutableStateFlow(LibraryGroupMode.FOLDER)
    private val _sortMode = MutableStateFlow(LibrarySortMode.TITLE_ASC)
    private val _sortDescending = MutableStateFlow(false)
    private val _secondarySortMode = MutableStateFlow(LibrarySecondarySortMode.FILENAME_ASC)
    private val _showFilename = MutableStateFlow(false)
    private val _selectedShelf = MutableStateFlow<LibraryShelf?>(null)

    private val booksWithProgress = bookReader.observeLibrary()
        .flatMapLatest { books ->
            if (books.isEmpty()) {
                flowOf(emptyList())
            } else {
                val progressFlows = books.map { book ->
                    progressReader.observe(book.id)
                        .let { flow -> combine(flowOf(book), flow) { b, p -> b to p } }
                }
                combine(progressFlows) { pairs -> pairs.toList() }
            }
        }

    private val booksWithTags = combine(
        booksWithProgress,
        bookTagDao?.observeAll() ?: flowOf<List<BookTagEntity>>(emptyList()),
    ) { pairs, tags ->
        val tagsByBook = tags.groupBy { it.bookId }.mapValues { (_, values) -> values.map { it.tag }.sorted() }
        pairs.map { (book, progress) -> Triple(book, progress, tagsByBook[book.id].orEmpty()) }
    }

    private val basicControls = combine(_viewMode, _query, _selectedIds) { mode, query, ids ->
        Triple(mode, query, ids)
    }
    private val filterControls = combine(
        _selectedTag, _selectedFolder, _groupMode, _sortMode, _sortDescending,
        _secondarySortMode, _showFilename, _selectedShelf, _libraryMode,
    ) { values -> values.toList() }
    private val libraryControls = combine(basicControls, filterControls) { basic, filters ->
        basic to filters
    }

    val uiState: StateFlow<LibraryUiState> = combine(booksWithTags, libraryControls) { pairs, controls ->
        val (viewMode, query, selectedIds) = controls.first
        val selectedTag = controls.second[0] as String?
        val selectedFolder = controls.second[1] as String?
        val groupMode = controls.second[2] as LibraryGroupMode
        val sortMode = controls.second[3] as LibrarySortMode
        val sortDescending = controls.second[4] as Boolean
        val secondarySortMode = controls.second[5] as LibrarySecondarySortMode
        val showFilename = controls.second[6] as Boolean
        val selectedShelf = controls.second[7] as LibraryShelf?
        val libraryMode = controls.second[8] as LibraryMode
        val allCards = pairs.map { (book, progress, tags) ->
            book.toBookCard(progress).copy(tags = tags, sourceFolder = book.sourceFolder, filename = book.sourceFileName ?: uriFilename(book.uri), displayFilename = showFilename, sha256 = book.sha256)
        }
        val queryFiltered = if (query.isBlank()) allCards else {
            allCards.filter { card ->
                card.title.contains(query, ignoreCase = true) ||
                    card.filename.contains(query, ignoreCase = true)
            }
        }
        val filteredCards = queryFiltered
            .filter { card -> selectedTag == null || selectedTag in card.tags }
            .filter { card ->
                selectedFolder == null || card.sourceFolder == selectedFolder
            }
            .filter { card ->
                when (selectedShelf) {
                    LibraryShelf.READING, LibraryShelf.TO_READ, LibraryShelf.FINISHED ->
                        card.effectiveReadingShelf() == selectedShelf
                    LibraryShelf.SERIES -> !card.seriesName.isNullOrBlank()
                    null -> true
                }
            }
            .let { cards ->
                val primary: Comparator<BookCard> = when (sortMode) {
                    LibrarySortMode.TITLE_ASC -> compareBy { it.title.lowercase() }
                    LibrarySortMode.AUTHOR_ASC -> compareBy { it.author.orEmpty().lowercase() }
                    LibrarySortMode.FILENAME_ASC -> compareBy { it.filename.lowercase() }
                    LibrarySortMode.ADDED_AT_ASC -> compareBy { it.addedAt }
                    LibrarySortMode.LAST_OPENED_AT_ASC -> compareBy { it.lastOpenedAt ?: Long.MIN_VALUE }
                    LibrarySortMode.PROGRESS_ASC -> compareBy { it.percent }
                }
                val directed = if (sortDescending) primary.reversed() else primary
                val secondary: Comparator<BookCard> = when (secondarySortMode) {
                    LibrarySecondarySortMode.TITLE_ASC -> compareBy { it.title.lowercase() }
                    LibrarySecondarySortMode.TITLE_DESC -> compareByDescending { it.title.lowercase() }
                    LibrarySecondarySortMode.AUTHOR_ASC -> compareBy { it.author.orEmpty().lowercase() }
                    LibrarySecondarySortMode.AUTHOR_DESC -> compareByDescending { it.author.orEmpty().lowercase() }
                    LibrarySecondarySortMode.FILENAME_ASC -> compareBy { it.filename.lowercase() }
                    LibrarySecondarySortMode.FILENAME_DESC -> compareByDescending { it.filename.lowercase() }
                    LibrarySecondarySortMode.ADDED_NEWEST -> compareByDescending { it.addedAt }
                    LibrarySecondarySortMode.ADDED_OLDEST -> compareBy { it.addedAt }
                    LibrarySecondarySortMode.OPENED_NEWEST -> compareByDescending { it.lastOpenedAt ?: Long.MIN_VALUE }
                    LibrarySecondarySortMode.OPENED_OLDEST -> compareBy { it.lastOpenedAt ?: Long.MIN_VALUE }
                }
                cards.sortedWith(directed.then(secondary))
            }
        // Build only the immediate children of the currently-open real folder.
        // Folder paths are relative SAF paths such as "Books/Fantasy/Series".
        val folderPaths = allCards.mapNotNull { it.sourceFolder?.takeIf(String::isNotBlank) }
        val childFolderPaths = folderPaths.mapNotNull { path ->
            val prefix = selectedFolder?.let { "$it/" }
            if (prefix != null) {
                if (!path.startsWith(prefix)) null
                else path.removePrefix(prefix).substringBefore('/').takeIf(String::isNotBlank)?.let { "$prefix$it" }
            } else {
                path.substringBefore('/').takeIf(String::isNotBlank)
            }
        }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            .let { paths -> if (sortDescending) paths.reversed() else paths }
        val folders = childFolderPaths.map { path ->
            val descendantBooks = allCards.filter { card ->
                val source = card.sourceFolder.orEmpty()
                source == path || source.startsWith("$path/")
            }
            LibraryFolderCard(
                name = path.substringAfterLast('/'),
                path = path,
                count = descendantBooks.size,
                sampleCovers = descendantBooks.take(4).map { it.coverPath },
            )
        }
        val continueCards = filteredCards
            .filter { it.effectiveReadingShelf() == LibraryShelf.READING && it.percent > 0f && it.percent < 0.999f && it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt }
            .take(4)
        val continueCard = continueCards.firstOrNull()
        // Keep this rail for individually imported books; folder books are browsed in their hierarchy.
        // SHA-256 suppresses an individual copy when the same EPUB is present in a folder.
        // Titles are deliberately not used as identity.
        val folderImportHashes = allCards
            .filter { !it.sourceFolder.isNullOrBlank() }
            .mapNotNull { it.sha256?.lowercase() }
            .toSet()
        val recentlyOpened = filteredCards
            .filter { it.lastOpenedAt != null && it.sourceFolder.isNullOrBlank() }
            .filter { it.sha256.isNullOrBlank() || it.sha256.lowercase() !in folderImportHashes }
            .sortedByDescending { it.lastOpenedAt }
            .take(8)
        val newestBooks = filteredCards.sortedByDescending { it.addedAt }.take(8)
        val visibleBooks = if (libraryMode == LibraryMode.FOLDER && selectedFolder == null && selectedShelf == null && selectedTag == null && query.isBlank() && groupMode == LibraryGroupMode.FOLDER) {
            // Folder-imported books are reached through their real folder container.
            // In Folder mode, only books outside imported folders appear at root. Other grouping modes
            // intentionally operate over the full library so Series/Author groups span folders.
            filteredCards.filter { it.sourceFolder.isNullOrBlank() }
        } else {
            // Search, tag, and shelf selections intentionally span folders and show matching books together.
            filteredCards
        }
        LibraryUiState(
            libraryMode = libraryMode,
            books = visibleBooks,
            continueCard = continueCard,
            continueCards = continueCards,
            viewMode = viewMode,
            query = query,
            selectedIds = selectedIds,
            availableTags = allCards.flatMap { it.tags }.distinct().sorted(),
            selectedTag = selectedTag,
            folders = if (libraryMode == LibraryMode.FOLDER) folders else emptyList(),
            selectedFolder = selectedFolder,
            groupMode = groupMode,
            sortMode = sortMode,
            sortDescending = sortDescending,
            secondarySortMode = secondarySortMode,
            showFilename = showFilename,
            totalBookCount = allCards.size,
            selectedShelf = selectedShelf,
            readingCount = allCards.count { it.effectiveReadingShelf() == LibraryShelf.READING },
            toReadCount = allCards.count { it.effectiveReadingShelf() == LibraryShelf.TO_READ },
            seriesCount = allCards.count { !it.seriesName.isNullOrBlank() },
            finishedCount = allCards.count { it.effectiveReadingShelf() == LibraryShelf.FINISHED },
            recentlyOpened = recentlyOpened,
            newestBooks = newestBooks,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(),
    )

    fun setViewMode(mode: LibraryViewMode) { _viewMode.value = mode }
    fun setLibraryMode(mode: LibraryMode) {
        if (_libraryMode.value == mode) return
        _libraryMode.value = mode
        _selectedFolder.value = null
        _selectedShelf.value = null
        _selectedTag.value = null
        _groupMode.value = if (mode == LibraryMode.FOLDER) LibraryGroupMode.FOLDER else LibraryGroupMode.NONE
        _selectedIds.value = emptySet()
    }
    fun setQuery(query: String) { _query.value = query }
    fun setTagFilter(tag: String?) { _selectedTag.value = tag }
    fun setFolderFilter(folder: String?) { _selectedFolder.value = folder; _selectedShelf.value = null }
    fun setGroupMode(mode: LibraryGroupMode) { _groupMode.value = mode }
    fun setSortMode(mode: LibrarySortMode) {
        _sortMode.value = mode
        val secondaryConflicts = when (mode) {
            LibrarySortMode.TITLE_ASC -> _secondarySortMode.value.name.startsWith("TITLE_")
            LibrarySortMode.AUTHOR_ASC -> _secondarySortMode.value.name.startsWith("AUTHOR_")
            LibrarySortMode.FILENAME_ASC -> _secondarySortMode.value.name.startsWith("FILENAME_")
            LibrarySortMode.ADDED_AT_ASC -> _secondarySortMode.value.name.startsWith("ADDED_")
            LibrarySortMode.LAST_OPENED_AT_ASC -> _secondarySortMode.value.name.startsWith("OPENED_")
            LibrarySortMode.PROGRESS_ASC -> false
        }
        if (secondaryConflicts) {
            _secondarySortMode.value = if (mode == LibrarySortMode.FILENAME_ASC) {
                LibrarySecondarySortMode.TITLE_ASC
            } else {
                LibrarySecondarySortMode.FILENAME_ASC
            }
        }
    }
    fun setSortDescending(descending: Boolean) { _sortDescending.value = descending }
    fun setSecondarySortMode(mode: LibrarySecondarySortMode) {
        val conflicts = when (_sortMode.value) {
            LibrarySortMode.TITLE_ASC -> mode.name.startsWith("TITLE_")
            LibrarySortMode.AUTHOR_ASC -> mode.name.startsWith("AUTHOR_")
            LibrarySortMode.FILENAME_ASC -> mode.name.startsWith("FILENAME_")
            LibrarySortMode.ADDED_AT_ASC -> mode.name.startsWith("ADDED_")
            LibrarySortMode.LAST_OPENED_AT_ASC -> mode.name.startsWith("OPENED_")
            LibrarySortMode.PROGRESS_ASC -> false
        }
        if (!conflicts) _secondarySortMode.value = mode
    }
    fun setShowFilename(show: Boolean) { _showFilename.value = show }
    fun setShelfFilter(shelf: LibraryShelf?) {
        _selectedShelf.value = if (_selectedShelf.value == shelf) null else shelf
        _selectedFolder.value = null
    }

    fun toggleSelection(bookId: String) {
        _selectedIds.value = _selectedIds.value.let { if (bookId in it) it - bookId else it + bookId }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun setReadingStatus(bookId: String, shelf: LibraryShelf) {
        if (shelf == LibraryShelf.SERIES) return
        val dao = bookDao ?: return
        val status = when (shelf) {
            LibraryShelf.READING -> "READING"
            LibraryShelf.TO_READ -> "TO_READ"
            LibraryShelf.FINISHED -> "FINISHED"
            LibraryShelf.SERIES -> return
        }
        viewModelScope.launch { dao.updateReadingStatus(bookId, status) }
    }

    fun updateBookMetadata(bookId: String, title: String, author: String?, seriesName: String?, seriesPosition: Double?) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val dao = bookDao ?: return
        viewModelScope.launch {
            dao.updateMetadata(bookId, cleanTitle, author?.trim()?.takeIf { it.isNotEmpty() }, seriesName?.trim()?.takeIf { it.isNotEmpty() }, seriesPosition)
        }
    }

    fun updateBookCover(bookId: String, coverPath: String) {
        val dao = bookDao ?: return
        viewModelScope.launch {
            val oldPath = dao.getById(bookId)?.coverPath
            dao.updateCoverPath(bookId, coverPath)
            if (!oldPath.isNullOrBlank() && oldPath != coverPath) {
                runCatching { File(oldPath).delete() }
            }
        }
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (playingBookIsSelected(playbackStopper.state.value, ids)) playbackStopper.stop()
            ids.forEach { bookDeleter.delete(it) }
            clearSelection()
        }
    }

    private fun BookEntity.toBookCard(progress: ProgressEntity?): BookCard {
        val percent = progress?.percent ?: 0f
        return BookCard(
            id = id,
            title = title,
            author = author,
            coverPath = coverPath,
            percent = percent,
            isReading = when (readingStatus) { "READING" -> true; "TO_READ", "FINISHED" -> false; else -> percent > 0f && percent < 0.999f },
            addedAt = addedAt,
            lastOpenedAt = lastOpenedAt,
            seriesName = seriesName,
            seriesPosition = seriesPosition,
            readingStatus = readingStatus,
        )
    }

    private fun uriFilename(uri: String?): String {
        if (uri == null) return ""
        val filename = uri.substringAfterLast('/')
        return try { java.net.URLDecoder.decode(filename, "UTF-8") } catch (_: Exception) { filename }
    }
}
