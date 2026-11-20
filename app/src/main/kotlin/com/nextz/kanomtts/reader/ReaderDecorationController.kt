package com.nextz.kanomtts.reader

import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.reader.decoration.BookmarkDecorations
import com.nextz.kanomtts.reader.decoration.SentenceHighlightProbe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Owns all navigator-decoration concerns for the reader.
 *
 * Three independent decoration groups are managed:
 *  - "bookmarks" — underline decorations for persisted bookmarks (rose tint).
 *  - "tts"       — single Highlight decoration tracking the sentence being spoken.
 *  - "probe"     — DEBUG-only full-publication sentence-boundary visualization.
 *
 * Each public method is a fire-and-forget: it launches a coroutine on [scope]
 * (mirroring the pattern in ReaderViewModel before this extraction) and casts
 * the stored navigator to [DecorableNavigator] at call time, so a null/unbound
 * navigator is safely ignored.
 */
class ReaderDecorationController(
    private val scope: CoroutineScope,
    private val roseArgb: Int,
) {

    private var nav: EpubNavigatorFragment? = null

    /** Bind the navigator. Must be called before any apply* method has effect. */
    fun bind(navigator: EpubNavigatorFragment) {
        nav = navigator
    }

    /** Drop the navigator when the fragment view is destroyed; apply* become no-ops until rebind. */
    fun unbind() { nav = null }

    /**
     * Push the "bookmarks" decoration group (group-replace semantics).
     * Called after every bookmark change and on bindNavigator.
     */
    fun applyBookmarks(bookmarks: List<BookmarkEntity>) {
        val n = nav ?: return
        scope.launch {
            (n as? DecorableNavigator)?.applyDecorations(
                decorations = BookmarkDecorations.toDecorations(bookmarks, roseArgb),
                group = "bookmarks",
            )
        }
    }

    /**
     * Push the "tts" decoration group.
     *
     * When [locator] is non-null, applies a single rose Highlight decoration
     * marking the sentence currently being spoken. When null, clears the group.
     */
    fun applyTts(locator: Locator?) {
        val n = nav ?: return
        scope.launch {
            val decorations: List<Decoration> = if (locator != null) {
                listOf(
                    Decoration(
                        id = "tts-current",
                        locator = locator,
                        style = Decoration.Style.Highlight(tint = roseArgb, isActive = true),
                    )
                )
            } else {
                emptyList()
            }
            (n as? DecorableNavigator)?.applyDecorations(decorations, group = "tts")
        }
    }

    /**
     * DEBUG-ONLY: apply a rose Highlight decoration for every tokenised sentence in
     * [publication] using group "probe". Validates sentence-boundary correctness and
     * Readium Decoration rendering. Guard with [BuildConfig.DEBUG] at the call site.
     */
    fun runProbe(publication: Publication) {
        val n = nav ?: return
        scope.launch {
            (n as? DecorableNavigator)?.applyDecorations(
                decorations = SentenceHighlightProbe.decorations(publication, SentenceHighlightProbe.ROSE_ARGB),
                group = "probe",
            )
        }
    }

    /**
     * DEBUG-ONLY: clear the "probe" group. Safe when no probe is active.
     * Guard with [BuildConfig.DEBUG] at the call site.
     */
    fun clearProbe() {
        val n = nav ?: return
        scope.launch {
            (n as? DecorableNavigator)?.applyDecorations(emptyList(), group = "probe")
        }
    }
}
