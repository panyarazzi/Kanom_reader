package com.nextz.kanomtts.reader

/** Formats the chapter-local page indicator using Readium's one-based page number. */
internal fun formatChapterProgress(
    pageNumber: Int?,
    totalPages: Int?,
    chapterTitle: String?,
): String? {
    if (pageNumber == null || totalPages == null || totalPages <= 0) return null
    val current = pageNumber.coerceIn(1, totalPages)
    val title = chapterTitle?.trim().orEmpty()
    return if (title.isEmpty()) "$current/$totalPages" else "$current/$totalPages $title"
}
