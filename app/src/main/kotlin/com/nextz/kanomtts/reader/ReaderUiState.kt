package com.nextz.kanomtts.reader

import androidx.fragment.app.FragmentFactory
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.data.prefs.KanomPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Ready(
        val book: BookEntity,
        val publication: Publication,
        val fragmentFactory: FragmentFactory,
        val initialLocator: Locator?,
        val chromeVisible: Boolean = true,
        val toc: List<Link> = emptyList(),
        val prefs: KanomPreferences = KanomPreferences(),
        val bookmarks: List<BookmarkEntity> = emptyList(),
        /** Latest reading position; used to focus + mark the current chapter in the ToC sheet. */
        val currentLocator: Locator? = null,
        /** One-based page number within the current chapter, supplied by Readium pagination. */
        val chapterPageNumber: Int? = null,
        /** Total rendered pages in the current chapter/resource, supplied by Readium. */
        val chapterPageCount: Int? = null,
        /** Locator delivered with the same pagination callback as chapterPageNumber/count. */
        val chapterProgressLocator: Locator? = null,
    ) : ReaderUiState
}
