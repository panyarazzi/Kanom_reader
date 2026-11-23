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
    private val _sortMode = MutableStateFlow(LibrarySortMode.TITLE_ASC)
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
    private val filterControls = combine(_selectedTag, _selectedFolder, _sortMode, _showFilename, _selectedShelf) { tag, folder, sort, showFilename, shelf ->
        listOf(tag, folder, sort, showFilename, shelf)
    }
    private val libraryControls = combine(basicControls, filterControls) { basic, filters ->
        basic to filters
    }

    val uiState: StateFlow<LibraryUiState> = combine(booksWithTags, libraryControls) { pairs, controls ->
        val (viewMode, query, selectedIds) = controls.first
        val selectedTag = controls.second[0] as String?
        val selectedFolder = controls.second[1] as String?
        val sortMode = controls.second[2] as LibrarySortMode
        val showFilename = controls.second[3] as Boolean
        val selectedShelf = controls.second[4] as LibraryShelf?
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
            .filter { card -> selectedFolder == null || card.sourceFolder == selectedFolder }
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
                when (sortMode) {
                    LibrarySortMode.TITLE_ASC -> cards.sortedBy { it.title.lowercase() }
                    LibrarySortMode.TITLE_DESC -> cards.sortedByDescending { it.title.lowercase() }
                    LibrarySortMode.FILENAME_ASC -> cards.sortedBy { it.filename.lowercase() }
                    LibrarySortMode.FILENAME_DESC -> cards.sortedByDescending { it.filename.lowercase() }
                }
            }
        val folders = allCards.filter { !it.sourceFolder.isNullOrBlank() }
            .groupBy { it.sourceFolder!! }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, cards) -> LibraryFolderCard(name, cards.size, cards.take(4).map { it.coverPath }) }
        val continueCard = filteredCards
            .filter { it.percent > 0f && it.percent < 0.999f && it.lastOpenedAt != null }
            .maxByOrNull { it.lastOpenedAt!! }
        val recentlyOpened = filteredCards
            .filter { it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt }
            .take(8)
        val newestBooks = filteredCards.sortedByDescending { it.addedAt }.take(8)
        LibraryUiState(
            books = filteredCards,
            continueCard = continueCard,
            viewMode = viewMode,
            query = query,
            selectedIds = selectedIds,
            availableTags = allCards.flatMap { it.tags }.distinct().sorted(),
            selectedTag = selectedTag,
            folders = folders,
            selectedFolder = selectedFolder,
            sortMode = sortMode,
            showFilename = showFilename,
            totalBookCount = allCards.size,
            selectedShelf = selectedShelf,
            readingCount = allCards.count { it.percent > 0f && it.percent < 0.999f },
            toReadCount = allCards.count { it.percent <= 0f },
            seriesCount = allCards.mapNotNull { it.sourceFolder?.takeIf(String::isNotBlank) }.distinct().size,
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
    fun setSortMode(mode: LibrarySortMode) { _sortMode.value = mode }
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
