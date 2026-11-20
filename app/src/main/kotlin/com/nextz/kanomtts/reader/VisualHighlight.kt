package com.nextz.kanomtts.reader

import com.nextz.kanomtts.reader.engine.ThaiZwspInjector
import com.nextz.kanomtts.text.replacement.ReplacementEngine
import org.readium.r2.shared.publication.Locator

/**
 * Rewrites a TTS locator's text quote so Readium matches it against the RENDERED (displayed) DOM
 * rather than the clean spoken text. Two display-only transforms, applied in the same order the
 * display publication applies them (see PublicationProvider.openForDisplay):
 *
 *  1. Visual Replacement — when on, the shown text differs from the spoken sentence, so the
 *     highlight quote must carry the replaced text ([engine]).
 *  2. Thai word-break — when [thaiWordBreak] is on, the DOM has U+200B injected at Thai word
 *     boundaries ([ThaiZwspInjector]). Readium's TextQuoteAnchor matcher does NOT normalize
 *     U+200B, so a clean quote fails to match a ZWSP-peppered paragraph; the match failure makes
 *     navigation fall back to the paragraph start (wrong page when a sentence spans a page break).
 *     Injecting the same ZWSP into the quote restores the match → correct page-turn AND highlight.
 *
 * Best-effort: if the transformed quote still doesn't match on device, Readium renders no
 * highlight (no crash). The spoken TTS text is never affected — only the locator quote.
 */
object VisualHighlight {
    private const val ZWSP = '​'

    fun transformQuote(
        locator: Locator?,
        engine: ReplacementEngine?,
        thaiWordBreak: Boolean = false,
    ): Locator? {
        if (locator == null) return null
        if (engine == null && !thaiWordBreak) return locator
        val text = locator.text
        val highlight = text.highlight ?: return locator  // nothing to anchor without a quote

        // Visual replacement on the highlight (mirrors the displayed DOM); before/after kept as-is.
        val before = text.before.orEmpty()
        val replacedHighlight = if (engine != null) engine.apply(highlight) else highlight
        val after = text.after.orEmpty()

        if (!thaiWordBreak) {
            return locator.copy(text = text.copy(before = before, highlight = replacedHighlight, after = after))
        }

        // ZWSP injection. The display DOM injects U+200B per word boundary over a WHOLE text node,
        // and ICU Thai boundaries are context-sensitive — so injecting each fragment separately
        // produces different positions and the quote fails to match (wrong `before` length also
        // throws off the fuzzy matcher's location). Inject over the full contiguous quote
        // (before+highlight+after) so boundaries match the node, then re-split at the highlight's
        // mapped offsets.
        val full = before + replacedHighlight + after
        val (displayed, map) = ThaiZwspInjector.injectTextMapped(full)
        val hStart = map[before.length]
        var hEnd = map[before.length + replacedHighlight.length]
        // A boundary ZWSP sitting exactly at the sentence end belongs to the gap, not the highlight.
        if (hEnd > hStart && displayed[hEnd - 1] == ZWSP) hEnd--

        return locator.copy(
            text = text.copy(
                before = displayed.substring(0, hStart),
                highlight = displayed.substring(hStart, hEnd),
                after = displayed.substring(hEnd),
            ),
        )
    }
}
