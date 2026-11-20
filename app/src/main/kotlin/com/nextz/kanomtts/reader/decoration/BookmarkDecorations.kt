package com.nextz.kanomtts.reader.decoration

import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import org.readium.r2.navigator.Decoration

/**
 * Maps persisted [BookmarkEntity] list to Readium [Decoration] list (Underline style, rose tint).
 *
 * [roseArgb] defaults to KanomColors.highlight (0xFFFF5E9C) so callers in tests or non-Compose
 * contexts can omit it; the VM passes the live theme value for correctness.
 *
 * Entries whose locator JSON cannot be decoded are silently filtered out.
 *
 * Decoration timing note: [applyDecorations] only renders once the WebView page is loaded.
 * The correct approach (applied on bind + on every bookmarks change) is implemented in
 * ReaderViewModel. Whether the outline appears immediately after add is a Task 10 device check.
 */
object BookmarkDecorations {

    // RoseHighlight = Color(0xFFFF5E9C) → ARGB int
    private const val DEFAULT_ROSE_ARGB = 0xFFFF5E9C.toInt()

    fun toDecorations(
        bookmarks: List<BookmarkEntity>,
        roseArgb: Int = DEFAULT_ROSE_ARGB,
    ): List<Decoration> = bookmarks.mapNotNull { entity ->
        LocatorSerializer.decode(entity.locator)?.let { locator ->
            Decoration(
                id = "bm-${entity.id}",
                locator = locator,
                style = Decoration.Style.Underline(tint = roseArgb, isActive = false),
            )
        }
    }
}
