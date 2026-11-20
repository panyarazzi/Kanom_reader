package com.nextz.kanomtts.reader

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

/**
 * Pure logic for flattening nested Readium TOC links and focusing the entry matching
 * the reader's current resource.
 */
@OptIn(ExperimentalReadiumApi::class)
object ReaderTocFocus {

    /** A rendered TOC row retaining its original Link and nesting depth. */
    data class Entry(val link: Link, val depth: Int)

    /**
     * Flatten the hierarchy depth-first, preserving source order and the original Link objects.
     * Every child is included, with depth 0 for root entries, 1 for their children, etc.
     */
    fun flatten(toc: List<Link>): List<Entry> = buildList {
        fun append(link: Link, depth: Int) {
            add(Entry(link, depth))
            link.children.forEach { child -> append(child, depth + 1) }
        }
        toc.forEach { link -> append(link, 0) }
    }

    /**
     * Index in [flatten]'s rendered row list for the first matching TOC entry in depth-first order.
     *
     * Prefer an exact href (including fragment) so sibling sections in the same XHTML
     * resource can be focused independently. If no exact fragment match exists, fall back
     * to the first entry for the same resource, ignoring fragments.
     */
    fun currentIndex(toc: List<Link>, currentHref: Url?): Int? {
        if (currentHref == null) return null
        val entries = flatten(toc)
        val href = currentHref.toString()
        val exactIndex = entries.indexOfFirst { it.link.href.toString() == href }
        if (exactIndex >= 0) return exactIndex

        val targetResource = href.substringBefore('#')
        val targetFragment = href.substringAfter('#', missingDelimiterValue = "")
        if (targetFragment.isNotEmpty()) {
            val fragmentIndex = entries.indexOfFirst { entry ->
                val entryHref = entry.link.href.toString()
                entryHref.substringBefore('#') == targetResource &&
                    entryHref.substringAfter('#', missingDelimiterValue = "") == targetFragment
            }
            if (fragmentIndex >= 0) return fragmentIndex
        }

        return entries.indexOfFirst { entry ->
            entry.link.href.toString().substringBefore('#') == targetResource
        }.takeIf { it >= 0 }
    }
}
