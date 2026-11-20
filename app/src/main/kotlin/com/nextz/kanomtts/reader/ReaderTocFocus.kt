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
     * Matching is fragment-insensitive: a TOC href "chapter.xhtml#section" matches a
     * locator in "chapter.xhtml#paragraph". Because entries are checked in flattened
     * order, a parent wins when it shares the same resource href with its descendants.
     */
    fun currentIndex(toc: List<Link>, currentHref: Url?): Int? {
        if (currentHref == null) return null
        val target = currentHref.toString().substringBefore('#')
        return flatten(toc).indexOfFirst { entry ->
            entry.link.href.toString().substringBefore('#') == target
        }.takeIf { it >= 0 }
    }
}
