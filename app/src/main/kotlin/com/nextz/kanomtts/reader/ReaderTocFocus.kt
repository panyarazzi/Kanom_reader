package com.nextz.kanomtts.reader

import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

/**
 * Pure logic for flattening nested Readium TOC links, tracking their tree paths,
 * and determining which branches must be expanded to reveal the current location.
 */
@OptIn(ExperimentalReadiumApi::class)
object ReaderTocFocus {

    /** A rendered TOC row retaining its original Link and stable tree path. */
    data class Entry(
        val link: Link,
        val depth: Int,
        val path: List<Int> = emptyList(),
        val hasChildren: Boolean = link.children.isNotEmpty(),
    ) {
        val key: String get() = path.joinToString("/")
    }

    /** Flatten the complete hierarchy depth-first, preserving source order and Link objects. */
    fun flatten(toc: List<Link>): List<Entry> = buildList {
        fun append(link: Link, depth: Int, path: List<Int>) {
            add(Entry(link = link, depth = depth, path = path))
            link.children.forEachIndexed { index, child ->
                append(child, depth + 1, path + index)
            }
        }
        toc.forEachIndexed { index, link -> append(link, 0, listOf(index)) }
    }

    /** Paths for every row that can be expanded, used by the global Expand all action. */
    fun expandablePaths(toc: List<Link>): Set<String> =
        flatten(toc).filter { it.hasChildren }.mapTo(linkedSetOf()) { it.key }

    /**
     * Flatten only rows whose parent branches are expanded.
     * [expandedPaths] contains slash-separated [Entry.key] values.
     */
    fun visible(toc: List<Link>, expandedPaths: Set<String>): List<Entry> = buildList {
        fun append(link: Link, depth: Int, path: List<Int>) {
            val entry = Entry(link = link, depth = depth, path = path)
            add(entry)
            if (entry.hasChildren && entry.key in expandedPaths) {
                link.children.forEachIndexed { index, child ->
                    append(child, depth + 1, path + index)
                }
            }
        }
        toc.forEachIndexed { index, link -> append(link, 0, listOf(index)) }
    }

    /** Parent paths needed to make the current entry visible in the tree. */
    fun expandedAncestors(toc: List<Link>, currentHref: Url?): Set<String> {
        val index = currentIndex(toc, currentHref) ?: return emptySet()
        val path = flatten(toc).getOrNull(index)?.path ?: return emptySet()
        return (1 until path.size).map { length ->
            path.take(length).joinToString("/")
        }.toSet()
    }

    /**
     * Index in the complete depth-first list for the first matching ToC entry.
     * Prefer an exact href (including fragment), then fall back to the same resource.
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
