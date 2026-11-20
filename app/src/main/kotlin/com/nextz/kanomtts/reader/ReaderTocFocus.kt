package com.nextz.kanomtts.reader

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

/**
 * Pure logic for highlighting / focusing the ToC entry the user is currently reading.
 *
 * The ToC the sheet renders is the publication's [Publication.tableOfContents] as a flat
 * top-level list — only the root entries are shown as rows. This object returns indices into
 * that same top-level list so that [scrollToItem] and the "isCurrent" highlight land on the
 * correct rendered row even when the ToC has nested children.
 */
@OptIn(ExperimentalReadiumApi::class)
object ReaderTocFocus {

    /**
     * Index into the TOP-LEVEL [toc] list (the rows the sheet renders) of the entry whose own
     * href OR any descendant's href matches [currentHref]'s resource, or null if there is no
     * match (or [currentHref] is null).
     *
     * Matching is fragment-insensitive: a ToC entry "ch2.xhtml#s3" matches a reading position at
     * "ch2.xhtml#p9" because both resolve to the same resource ("ch2.xhtml"). When the current
     * position is inside a nested sub-section the TOP-LEVEL ancestor's index is returned so the
     * scroll and highlight target the row that is actually rendered.
     */
    fun currentIndex(toc: List<Link>, currentHref: Url?): Int? {
        if (currentHref == null) return null
        val target = currentHref.toString().substringBefore('#')
        val idx = toc.indexOfFirst { subtreeMatches(it, target) }
        return idx.takeIf { it >= 0 }
    }

    private fun subtreeMatches(link: Link, target: String): Boolean =
        link.href.toString().substringBefore('#') == target ||
            link.children.any { subtreeMatches(it, target) }
}
