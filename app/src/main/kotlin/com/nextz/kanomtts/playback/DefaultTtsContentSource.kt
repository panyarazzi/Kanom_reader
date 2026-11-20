package com.nextz.kanomtts.playback

import android.util.Log
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import com.nextz.kanomtts.reader.engine.PublicationProvider
import com.nextz.kanomtts.reader.stripZwsp
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.NormalizerOptions
import com.nextz.kanomtts.text.pipeline.TextPipeline
import com.nextz.kanomtts.text.pipeline.TextTransform
import com.nextz.kanomtts.text.replacement.ReplacementEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import javax.inject.Inject

/**
 * Real [TtsContentSource] that streams [SentenceUtterance]s from a Readium
 * [Publication] using the content-iterator API (recon §7c). The publication is
 * opened un-transformed (no ZWSP injection) so replacement rules and TTS text
 * are clean.
 *
 * The returned flow is cold: each collection opens the publication, iterates its
 * content via the suspend [Content.Iterator.hasNext] / non-suspend [Content.Iterator.next]
 * contract, and closes the publication in a finally block — even on cancellation.
 */
@OptIn(ExperimentalReadiumApi::class)
class DefaultTtsContentSource @Inject constructor(
    private val publications: PublicationProvider,
    private val books: BookReader,
    private val rules: ReplacementRuleRepository,
    private val prefs: ReaderPreferences,
) : TtsContentSource {

    override fun stream(
        bookId: String,
        fromLocator: Locator?,
        skipToSelection: Boolean,
    ): Flow<SentenceUtterance> = flow {
        val book = books.get(bookId) ?: return@flow
        val pub = publications.open(book.uri) ?: return@flow

        try {
            val engine = ReplacementEngine(rules.observeEffective(bookId).first())
            val prefsSnapshot = prefs.preferences.first()
            val pipeline = TextPipeline(
                primary = langOf(book.lang),
                replacement = TextTransform { engine.apply(it) },
                options = NormalizerOptions(skipUrls = prefsSnapshot.skipUrls),
            )
            val byParagraph = prefsSnapshot.readingUnit == ReadingUnit.PARAGRAPH

            // Derive selection-skip parameters once, before the iterator loop.
            val selectionHighlight = if (skipToSelection) {
                stripZwsp(fromLocator?.text?.highlight.orEmpty()).trim().takeIf { it.isNotEmpty() }
            } else null
            val selectionProgression = fromLocator?.locations?.progression

            // matched=true when skipToSelection is off or no highlight is available (emit everything).
            // matched flips to true once we find the paragraph that contains the selection text.
            var matched = selectionHighlight == null
            var firstHref: String? = null

            val content = pub.content(fromLocator)
            if (content != null) {
                val it = content.iterator()
                while (it.hasNext()) {
                    val el = it.next()
                    if (el is Content.TextElement) {
                        if (!matched) {
                            val elHref = el.locator.href.toString()
                            // Fallback: if the resource href changed before any match was found,
                            // we moved past the starting resource — start emitting from here.
                            if (firstHref == null) {
                                firstHref = elHref
                            } else if (elHref != firstHref) {
                                Log.w("TTS", "Selection text not found in resource, starting from current position")
                                matched = true
                            }
                        }

                        if (!matched) {
                            // Check if this element is the paragraph containing the selection.
                            matched = matchesSelectionStart(
                                elementText = el.text,
                                elementProgression = el.locator.locations.progression,
                                selectionHighlight = selectionHighlight!!,
                                selectionProgression = selectionProgression,
                            )
                            if (!matched) continue  // skip this element
                        }

                        TtsUtteranceBuilder.build(
                            text = el.text,
                            locator = el.locator,
                            byParagraph = byParagraph,
                            pipeline = pipeline,
                            maxRunChars = MAX_RUN_CHARS,
                        ).forEach { emit(it) }
                    }
                }
            }
        } finally {
            pub.close()
        }
    }

    companion object {
        /**
         * Max characters per speak() call — a fixed margin below the platform's documented limit.
         * [android.speech.tts.TextToSpeech.getMaxSpeechInputLength] is a static constant of 4000
         * UTF-16 units (it is NOT engine-queryable), so we hard-code the margin rather than read it.
         */
        private const val MAX_RUN_CHARS = 3500

        fun langOf(bcp47: String?): Lang = when {
            bcp47 == null -> Lang.TH
            bcp47.startsWith("en", ignoreCase = true) -> Lang.EN
            bcp47.startsWith("ja", ignoreCase = true) -> Lang.JA
            bcp47.startsWith("zh", ignoreCase = true) -> Lang.ZH
            else -> Lang.TH
        }
    }
}
