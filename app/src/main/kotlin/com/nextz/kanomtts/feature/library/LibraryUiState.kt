package com.nextz.kanomtts.feature.library

enum class LibraryViewMode { GRID, LIST, DETAIL }

data class BookCard(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val percent: Float,
    val isReading: Boolean,
)

data class LibraryUiState(
    val books: List<BookCard> = emptyList(),
    val continueCard: BookCard? = null,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val query: String = "",
    val selectedIds: Set<String> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}
