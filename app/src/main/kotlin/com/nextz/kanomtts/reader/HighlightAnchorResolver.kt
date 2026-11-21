package com.nextz.kanomtts.reader
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
/**
 * Resolve a saved quote against the current display publication. Scan only the target
 * XHTML resource from its beginning, require an exact unambiguous match, then return a
 * quote-only Locator with no stale CFI/progression/DOM coordinates.
 */
@OptIn(ExperimentalReadiumApi::class)
object HighlightAnchorResolver {
    private const val MAX_RESOURCE_CHARS = 2_000_000
    private const val MAX_RESOURCE_ELEMENTS = 20_000
    suspend fun resolve(publication: Publication, savedLocator: Locator, thaiWordBreak: Boolean): Locator? {
        val quote = savedLocator.text.highlight?.takeIf { it.isNotBlank() } ?: return null
        val cleanText = savedLocator.text.copy(
            before = stripZwsp(savedLocator.text.before.orEmpty()),
            highlight = stripZwsp(quote),
            after = stripZwsp(savedLocator.text.after.orEmpty()),
        )
        val displayLocator = VisualHighlight.transformQuote(savedLocator.copy(text = cleanText), engine = null, thaiWordBreak = thaiWordBreak) ?: return null
        val quoteOnly = displayLocator.copy(locations = Locator.Locations())
        val iterator = publication.content(quoteOnly)?.iterator() ?: return null
        val resourceText = StringBuilder()
        var elements = 0
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element.locator.href != savedLocator.href) break
            if (element is Content.TextElement) {
                resourceText.append(element.text)
                if (resourceText.length > MAX_RESOURCE_CHARS) return null
            }
            elements++
            if (elements > MAX_RESOURCE_ELEMENTS) return null
        }
        if (!HighlightAnchorMatcher.hasUnambiguousExactMatch(resourceText.toString(), quoteOnly.text.highlight, quoteOnly.text.before, quoteOnly.text.after)) return null
        return quoteOnly
    }
}
