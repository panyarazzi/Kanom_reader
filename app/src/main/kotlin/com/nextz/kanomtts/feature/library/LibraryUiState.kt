package com.nextz.kanomtts.feature.library

enum class LibraryViewMode { GRID, LIST, DETAIL }
enum class LibrarySortMode(val label: String) {
    TITLE_ASC("ชื่อ A–Z"),
    TITLE_DESC("ชื่อ Z–A"),
    FILENAME_ASC("ชื่อไฟล์ A–Z"),
    FILENAME_DESC("ชื่อไฟล์ Z–A"),
}

data class BookCard(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val percent: Float,
    val isReading: Boolean,
    val tags: List<String> = emptyList(),
    val sourceFolder: String? = null,
    val filename: String = "",
    val displayFilename: Boolean = false,
) {
    val displayTitle: String get() = if (displayFilename && filename.isNotBlank()) filename else title
}

data class LibraryFolderCard(
    val name: String,
    val count: Int,
    val sampleCovers: List<String?>,
)

data class LibraryUiState(
    val books: List<BookCard> = emptyList(),
    val continueCard: BookCard? = null,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val query: String = "",
    val selectedIds: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val folders: List<LibraryFolderCard> = emptyList(),
    val selectedFolder: String? = null,
    val sortMode: LibrarySortMode = LibrarySortMode.TITLE_ASC,
    val showFilename: Boolean = false,
    val totalBookCount: Int = 0,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}
