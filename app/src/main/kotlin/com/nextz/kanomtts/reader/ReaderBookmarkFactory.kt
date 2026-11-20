package com.nextz.kanomtts.reader

import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import org.readium.r2.shared.publication.Locator

object ReaderBookmarkFactory {
    /**
     * Builds a [BookmarkEntity] from a [locator] and a pre-computed [snippet].
     *
     * The snippet is computed by the caller (the VM) because deriving a meaningful
     * passage excerpt requires the suspend Content-at-locator API; this function stays
     * pure so it can be unit-tested in isolation. [snippet] is stored verbatim.
     */
    fun build(bookId: String, locator: Locator, snippet: String, now: Long): BookmarkEntity =
        BookmarkEntity(
            bookId = bookId,
            locator = LocatorSerializer.encode(locator),
            snippet = snippet,
            createdAt = now,
        )
}
