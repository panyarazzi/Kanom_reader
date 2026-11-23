package com.nextz.kanomtts.reader

import org.readium.r2.shared.publication.Link
import java.net.URI
import java.net.URLDecoder
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


/**
 * Resolve the visible chapter name from the publication ToC instead of trusting
 * Locator.title, which is frequently absent or contains a resource-level fallback.
 * Nested ToC entries are searched recursively and resource fragments are ignored.
 */
internal fun resolveChapterTitle(
    locatorHref: String?,
    locatorTitle: String?,
    toc: List<Link>,
): String? {
    fun flatten(items: List<Link>): Sequence<Link> = items.asSequence().flatMap { link ->
        sequenceOf(link) + flatten(link.children)
    }

    fun canonicalHref(value: String): String {
        val withoutFragment = value.substringBefore('#').substringBefore('?')
        val path = runCatching { URI(withoutFragment).rawPath }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: withoutFragment
        // EPUB hrefs originate in publisher files and may contain malformed percent escapes.
        // A bad escape must not crash the reader's progress overlay.
        val decoded = runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }
            .getOrDefault(path)
        return decoded.trimStart('/')
    }

    val target = locatorHref?.takeIf { it.isNotBlank() }?.let(::canonicalHref)
    val tocTitle = if (target == null) null else flatten(toc)
        .filter { canonicalHref(it.href.toString()) == target }
        .mapNotNull { it.title?.trim()?.takeIf(::isUsefulChapterTitle) }
        .lastOrNull()

    return tocTitle ?: locatorTitle?.trim()?.takeIf(::isUsefulChapterTitle)
}


/**
 * Resolve the chapter title using the locator delivered with the pagination callback.
 * A later currentLocator emission may already point at the next chapter while the page
 * count still belongs to the previous chapter; never mix those two events.
 */
internal fun resolveProgressChapterTitle(
    paginationLocatorHref: String?,
    paginationLocatorTitle: String?,
    currentLocatorHref: String?,
    currentLocatorTitle: String?,
    toc: List<Link>,
): String? {
    return if (!paginationLocatorHref.isNullOrBlank()) {
        resolveChapterTitle(paginationLocatorHref, paginationLocatorTitle, toc)
    } else {
        resolveChapterTitle(currentLocatorHref, currentLocatorTitle, toc)
    }
}

private fun isUsefulChapterTitle(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.matches(Regex("(?i)^chapter$|^section$|^untitled$"))) return false
    if (value.matches(Regex("^เล่มที่\\s*\\[?\\d+]?\\s*$"))) return false
    return true
}


data class ProgressContent(
    val chapterPage: String?,
    val bookPercent: String?,
    val chapterTitle: String?,
    val bookTitle: String?,
    val time: String?,
    val battery: String?,
    val charging: Boolean,
)

data class ProgressLayout(
    val left: String,
    val center: String,
    val right: String,
)

private fun batteryProgressText(content: ProgressContent): String? =
    content.battery?.let { "B ${if (content.charging) "~" else ""}$it" }

internal fun progressSlotValue(slot: String, content: ProgressContent): String? = when (slot) {
    "CHAPTER_PAGE" -> content.chapterPage
    "BOOK_PERCENT" -> content.bookPercent
    "CHAPTER_TITLE" -> content.chapterTitle
    "BOOK_TITLE" -> content.bookTitle
    "TIME_BATTERY" -> listOfNotNull(content.time, batteryProgressText(content))
        .joinToString(" • ").ifBlank { null }
    "TIME" -> content.time
    "BATTERY", "CHARGING" -> batteryProgressText(content)
    else -> null
}

internal fun buildProgressContent(
    pageNumber: Int?,
    totalPages: Int?,
    chapterTitle: String?,
    bookTitle: String?,
    totalProgression: Double?,
    time: String?,
    batteryPercent: Int?,
    charging: Boolean,
): ProgressContent {
    val page = if (pageNumber != null && totalPages != null && totalPages > 0) {
        "${pageNumber.coerceIn(1, totalPages)}/$totalPages"
    } else null
    val percent = totalProgression?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)
        ?.let { String.format(Locale.US, "%.2f%%", it * 100.0) }
    return ProgressContent(
        chapterPage = page,
        bookPercent = percent,
        // The CHAPTER_TITLE slot must include chapter-local pagination before the title.
        // Keep whole-book percentage in its own slot; do not mix it into the chapter label.
        chapterTitle = formatChapterProgress(pageNumber, totalPages, chapterTitle)
            ?: chapterTitle?.trim()?.takeIf { it.isNotEmpty() },
        bookTitle = bookTitle?.trim()?.takeIf { it.isNotEmpty() },
        time = time,
        battery = batteryPercent?.coerceIn(0, 100)?.let { "$it%" },
        charging = charging,
    )
}

internal data class ProgressSlotValues(
    val left: String?,
    val center: String?,
    val right: String?,
)

internal fun progressSlotValues(layout: ProgressLayout, content: ProgressContent): ProgressSlotValues {
    val left = progressSlotValue(layout.left, content)
    val centerRaw = progressSlotValue(layout.center, content)
    val center = centerRaw?.takeUnless { it == left }
    val rightRaw = progressSlotValue(layout.right, content)
    val right = rightRaw?.takeUnless { it == left || it == center }
    return ProgressSlotValues(left, center, right)
}
