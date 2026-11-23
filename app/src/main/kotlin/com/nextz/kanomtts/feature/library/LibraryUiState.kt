package com.nextz.kanomtts.feature.library

enum class LibraryViewMode { GRID, LIST, DETAIL }
enum class LibraryShelf(val label: String) {
    READING("กำลังอ่าน"),
    TO_READ("รออ่าน"),
    SERIES("ซีรีส์/ชุดหนังสือ"),
    FINISHED("อ่านจบแล้ว"),
}

enum class LibrarySecondarySortMode(val label: String) {
    FILENAME_ASC("ชื่อไฟล์ A–Z"),
    FILENAME_DESC("ชื่อไฟล์ Z–A"),
}

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
    val addedAt: Long = 0L,
    val lastOpenedAt: Long? = null,
) {
    val displayTitle: String get() = if (displayFilename && filename.isNotBlank()) filename else title
}

data class LibraryFolderCard(
    val name: String,
    val path: String,
    val count: Int,
    val sampleCovers: List<String?>,
)

data class LibraryUiState(
    val books: List<BookCard> = emptyList(),
    val continueCard: BookCard? = null,
    val continueCards: List<BookCard> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val query: String = "",
    val selectedIds: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val folders: List<LibraryFolderCard> = emptyList(),
    val selectedFolder: String? = null,
    val sortMode: LibrarySortMode = LibrarySortMode.TITLE_ASC,
    val secondarySortMode: LibrarySecondarySortMode = LibrarySecondarySortMode.FILENAME_ASC,
    val showFilename: Boolean = false,
    val totalBookCount: Int = 0,
    val selectedShelf: LibraryShelf? = null,
    val readingCount: Int = 0,
    val toReadCount: Int = 0,
    val seriesCount: Int = 0,
    val finishedCount: Int = 0,
    val recentlyOpened: List<BookCard> = emptyList(),
    val newestBooks: List<BookCard> = emptyList(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}
