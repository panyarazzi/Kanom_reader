package com.nextz.kanomtts.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookEntity
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
) : ViewModel() {

    private val _viewMode = MutableStateFlow(LibraryViewMode.GRID)
    private val _query = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow(emptySet<String>())

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

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithProgress,
        _viewMode,
        _query,
        _selectedIds,
    ) { pairs, viewMode, query, selectedIds ->
        val bookById = pairs.associate { (b, _) -> b.id to b }
        val allCards = pairs.map { (book, progress) ->
            book.toBookCard(progress)
        }
        val filteredCards = if (query.isBlank()) allCards else {
            allCards.filter { card ->
                card.title.contains(query, ignoreCase = true) ||
                    uriFilename(bookById[card.id]?.uri).contains(query, ignoreCase = true)
            }
        }
        val continueCard = pairs
            .filter { (_, progress) ->
                val percent = progress?.percent ?: 0f
                percent > 0f && percent < 1f
            }
            .mapNotNull { (book, _) -> book.lastOpenedAt?.let { book } }
            .maxByOrNull { it.lastOpenedAt!! }
            ?.let { book -> allCards.find { it.id == book.id } }
        LibraryUiState(
            books = filteredCards,
            continueCard = continueCard,
            viewMode = viewMode,
            query = query,
            selectedIds = selectedIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(),
    )

    fun setViewMode(mode: LibraryViewMode) { _viewMode.value = mode }
    fun setQuery(query: String) { _query.value = query }

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
            isReading = percent > 0f && percent < 1f,
        )
    }

    private fun uriFilename(uri: String?): String {
        if (uri == null) return ""
        return uri.substringAfterLast('/').substringAfterLast('%').let {
            try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
        }
    }
}
