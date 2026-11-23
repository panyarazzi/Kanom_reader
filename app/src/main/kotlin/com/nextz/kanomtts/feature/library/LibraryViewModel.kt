package com.nextz.kanomtts.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookEntity
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
) : ViewModel() {

    private val _viewMode = MutableStateFlow(LibraryViewMode.GRID)
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
        _secondarySortMode, _showFilename, _selectedShelf,
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
        val allCards = pairs.map { (book, progress, tags) ->
            book.toBookCard(progress).copy(tags = tags, sourceFolder = book.sourceFolder, filename = book.sourceFileName ?: uriFilename(book.uri), displayFilename = showFilename)
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
                    LibraryShelf.READING -> card.percent > 0f && card.percent < 0.999f
                    LibraryShelf.TO_READ -> card.percent <= 0f
                    LibraryShelf.SERIES -> !card.sourceFolder.isNullOrBlank()
                    LibraryShelf.FINISHED -> card.percent >= 0.999f
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
            .filter { it.percent > 0f && it.percent < 0.999f && it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt }
            .take(4)
        val continueCard = continueCards.firstOrNull()
        val recentlyOpened = filteredCards
            .filter { it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt }
            .take(8)
        val newestBooks = filteredCards.sortedByDescending { it.addedAt }.take(8)
        val visibleBooks = if (selectedFolder == null && selectedShelf == null && selectedTag == null && query.isBlank()) {
            // Folder-imported books are reached through their real folder container.
            // Only books added outside a folder (future Classic mode) appear at root.
            filteredCards.filter { it.sourceFolder.isNullOrBlank() }
        } else {
            // Search, tag, and shelf selections intentionally span folders and show matching books together.
            filteredCards
        }
        LibraryUiState(
            books = visibleBooks,
            continueCard = continueCard,
            continueCards = continueCards,
            viewMode = viewMode,
            query = query,
            selectedIds = selectedIds,
            availableTags = allCards.flatMap { it.tags }.distinct().sorted(),
            selectedTag = selectedTag,
            folders = folders,
            selectedFolder = selectedFolder,
            groupMode = groupMode,
            sortMode = sortMode,
            sortDescending = sortDescending,
            secondarySortMode = secondarySortMode,
            showFilename = showFilename,
            totalBookCount = allCards.size,
            selectedShelf = selectedShelf,
            readingCount = allCards.count { it.percent > 0f && it.percent < 0.999f },
            toReadCount = allCards.count { it.percent <= 0f },
            seriesCount = allCards.count { !it.sourceFolder.isNullOrBlank() },
            finishedCount = allCards.count { it.percent >= 0.999f },
            recentlyOpened = recentlyOpened,
            newestBooks = newestBooks,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(),
    )

    fun setViewMode(mode: LibraryViewMode) { _viewMode.value = mode }
    fun setQuery(query: String) { _query.value = query }
    fun setTagFilter(tag: String?) { _selectedTag.value = tag }
    fun setFolderFilter(folder: String?) { _selectedFolder.value = folder; _selectedShelf.value = null }
    fun setGroupMode(mode: LibraryGroupMode) { _groupMode.value = mode }
    fun setSortMode(mode: LibrarySortMode) { _sortMode.value = mode }
    fun setSortDescending(descending: Boolean) { _sortDescending.value = descending }
    fun setSecondarySortMode(mode: LibrarySecondarySortMode) { _secondarySortMode.value = mode }
    fun setShowFilename(show: Boolean) { _showFilename.value = show }
    fun setShelfFilter(shelf: LibraryShelf?) {
        _selectedShelf.value = if (_selectedShelf.value == shelf) null else shelf
        _selectedFolder.value = null
    }

    fun toggleSelection(bookId: String) {
        _selectedIds.value = _selectedIds.value.let { if (bookId in it) it - bookId else it + bookId }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

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
            isReading = percent > 0f && percent < 0.999f,
            addedAt = addedAt,
            lastOpenedAt = lastOpenedAt,
        )
    }

    private fun uriFilename(uri: String?): String {
        if (uri == null) return ""
        val filename = uri.substringAfterLast('/')
        return try { java.net.URLDecoder.decode(filename, "UTF-8") } catch (_: Exception) { filename }
    }
}
