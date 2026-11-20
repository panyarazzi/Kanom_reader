package com.nextz.kanomtts.reader.decoration

import com.nextz.kanomtts.playback.SentenceLocators
import com.nextz.kanomtts.text.pipeline.SentenceTokenizer
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

/**
 * DEBUG-ONLY sentence-highlight de-risk harness (Task 8).
 *
 * Builds one rose [Decoration.Style.Highlight] per sentence across all [Content.TextElement]s
 * of the publication, using the same [SentenceTokenizer] and [Decoration] machinery that
 * Plan 07's karaoke highlight will use. Apply via group "probe" so it is independent of the
 * "bookmarks" group.
 *
 * Callers must guard with BuildConfig.DEBUG before calling [decorations] — this object itself
 * has no runtime guard so the suspend function can be kept pure and unit-testable.
 *
 * ZWSP note: when thaiWordBreak is ON (default), the display publication's content elements
 * contain U+200B characters injected by ThaiZwspInjector. SentenceTokenizer does not treat
 * U+200B as whitespace (it is non-breaking), so sentence boundaries are not mis-split. The
 * highlight substring includes ZWSP characters and should match the DOM text node (which also
 * has ZWSP). Whether Readium's Decoration engine correctly matches ZWSP-containing substrings
 * to DOM ranges is part of what this harness validates on device (Task 10 eyeball check).
 */
@OptIn(ExperimentalReadiumApi::class)
object SentenceHighlightProbe {

    // Matches BookmarkDecorations.DEFAULT_ROSE_ARGB — RoseHighlight = Color(0xFFFF5E9C).
    internal const val ROSE_ARGB = 0xFFFF5E9C.toInt()

    /**
     * Returns one [Decoration] per sentence found across all text elements of [publication].
     * Returns an empty list if the publication has no content service.
     *
     * @param publication the display publication (may have ZWSP-transformed text).
     * @param tint        ARGB int for the highlight colour; defaults to [ROSE_ARGB].
     */
    suspend fun decorations(
        publication: Publication,
        tint: Int = ROSE_ARGB,
    ): List<Decoration> {
        val content = publication.content() ?: return emptyList()
        val out = mutableListOf<Decoration>()
        content.elements()
            .filterIsInstance<Content.TextElement>()
            .forEachIndexed { ei, el ->
                SentenceTokenizer.tokenize(el.text).forEachIndexed { si, s ->
                    out += Decoration(
                        id = "sent-$ei-$si",
                        locator = SentenceLocators.forSentence(el.locator, s.start, s.end),
                        style = Decoration.Style.Highlight(tint = tint, isActive = true),
                    )
                }
            }
        return out
    }
}
