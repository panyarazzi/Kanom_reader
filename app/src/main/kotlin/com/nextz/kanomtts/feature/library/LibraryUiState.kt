package com.nextz.kanomtts.feature.library

enum class LibraryViewMode { GRID, LIST, DETAIL }
enum class LibraryMode { CLASSIC, FOLDER }
enum class LibraryShelf(val label: String) {
    READING("กำลังอ่าน"),
    TO_READ("รออ่าน"),
    SERIES("ซีรีส์/ชุดหนังสือ"),
    FINISHED("อ่านจบแล้ว"),
}

enum class LibraryGroupMode(val label: String) {
    NONE("ไม่จัดกลุ่ม"),
    FOLDER("โฟลเดอร์"),
    AUTHOR("ผู้แต่ง"),
    SERIES("ซีรีส์ / ชุดหนังสือ"),
    READING_STATUS("สถานะการอ่าน"),
    TAG("แท็ก"),
}

enum class SortDirection(val label: String) {
    ASC("A–Z / น้อยไปมาก / เก่าที่สุด"),
    DESC("Z–A / มากไปน้อย / ใหม่ที่สุด"),
}

enum class LibrarySortMode(val label: String, val ascendingLabel: String, val descendingLabel: String) {
    TITLE_ASC("ชื่อหนังสือ", "A–Z", "Z–A"),
    AUTHOR_ASC("ชื่อผู้แต่ง", "A–Z", "Z–A"),
    FILENAME_ASC("ชื่อไฟล์ต้นฉบับ", "A–Z", "Z–A"),
    ADDED_AT_ASC("วันที่เพิ่มเข้าคลัง", "เก่าที่สุด", "ใหม่ที่สุด"),
    LAST_OPENED_AT_ASC("วันที่เปิดอ่านล่าสุด", "เก่าที่สุด", "ล่าสุด"),
    PROGRESS_ASC("ความคืบหน้าในการอ่าน", "น้อยไปมาก", "มากไปน้อย"),
}

enum class LibrarySecondarySortMode(val label: String) {
    TITLE_ASC("ชื่อหนังสือ A–Z"),
    TITLE_DESC("ชื่อหนังสือ Z–A"),
    AUTHOR_ASC("ชื่อผู้แต่ง A–Z"),
    AUTHOR_DESC("ชื่อผู้แต่ง Z–A"),
    FILENAME_ASC("ชื่อไฟล์ A–Z"),
    FILENAME_DESC("ชื่อไฟล์ Z–A"),
    ADDED_NEWEST("วันที่เพิ่ม ใหม่ที่สุด"),
    ADDED_OLDEST("วันที่เพิ่ม เก่าที่สุด"),
    OPENED_NEWEST("วันที่เปิดอ่านล่าสุด"),
    OPENED_OLDEST("วันที่เปิดอ่านเก่าที่สุด"),
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
    val seriesName: String? = null,
    val seriesPosition: Double? = null,
    val sha256: String? = null,
    val readingStatus: String? = null,
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
    val libraryMode: LibraryMode = LibraryMode.FOLDER,
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
    val groupMode: LibraryGroupMode = LibraryGroupMode.FOLDER,
    val sortMode: LibrarySortMode = LibrarySortMode.TITLE_ASC,
    val sortDescending: Boolean = false,
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

internal fun BookCard.effectiveReadingShelf(): LibraryShelf = when (readingStatus) {
    "READING" -> LibraryShelf.READING
    "TO_READ" -> LibraryShelf.TO_READ
    "FINISHED" -> LibraryShelf.FINISHED
    else -> when {
        percent >= 0.999f -> LibraryShelf.FINISHED
        percent > 0f -> LibraryShelf.READING
        else -> LibraryShelf.TO_READ
    }
}
