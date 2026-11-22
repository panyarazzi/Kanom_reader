package com.nextz.kanomtts.reader

import java.util.Locale

/** Formats chapter-local pagination and Readium's whole-book progression. */
internal fun formatChapterProgress(
    pageNumber: Int?,
    totalPages: Int?,
    chapterTitle: String?,
    totalProgression: Double? = null,
): String? {
    if (pageNumber == null || totalPages == null || totalPages <= 0) return null
    val current = pageNumber.coerceIn(1, totalPages)
    val percentage = totalProgression
        ?.takeIf { it.isFinite() }
        ?.coerceIn(0.0, 1.0)
        ?.let { String.format(Locale.US, "%.2f%%", it * 100.0) }
    val title = chapterTitle?.trim().orEmpty()
    return listOfNotNull("$current/$totalPages", percentage, title.ifEmpty { null })
        .joinToString(" ")
}
