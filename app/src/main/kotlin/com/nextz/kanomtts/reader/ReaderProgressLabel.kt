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
        return URLDecoder.decode(path.replace("+", "%2B"), "UTF-8").trimStart('/')
    }

    val target = locatorHref?.takeIf { it.isNotBlank() }?.let(::canonicalHref)
    val tocTitle = if (target == null) null else flatten(toc)
        .filter { canonicalHref(it.href.toString()) == target }
        .mapNotNull { it.title?.trim()?.takeIf(::isUsefulChapterTitle) }
        .lastOrNull()

    return tocTitle ?: locatorTitle?.trim()?.takeIf(::isUsefulChapterTitle)
}

private fun isUsefulChapterTitle(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.matches(Regex("(?i)^chapter$|^section$|^untitled$"))) return false
    if (value.matches(Regex("^เล่มที่\\s*\\[?\\d+]?\\s*$"))) return false
    return true
}
