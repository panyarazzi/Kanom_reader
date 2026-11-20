package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.TextPipeline
import org.readium.r2.shared.publication.Locator

/**
 * Maps one content element's (text, locator) to the [SentenceUtterance]s to speak.
 *
 * Pure + side-effect free so it is unit-testable without a Readium publication.
 *  - byParagraph=false: one utterance per sentence (locator narrowed to the sentence range).
 *  - byParagraph=true: ONE utterance for the whole element (element locator), with one segment per
 *    same-language run — a pure-language paragraph becomes a single gapless speak.
 */
object TtsUtteranceBuilder {
    fun build(
        text: String,
        locator: Locator,
        byParagraph: Boolean,
        pipeline: TextPipeline,
        maxRunChars: Int,
    ): List<SentenceUtterance> =
        if (byParagraph) {
            val segments = pipeline.processParagraph(text, maxRunChars)
            if (segments.isEmpty()) emptyList() else listOf(SentenceUtterance(locator, segments))
        } else {
            pipeline.process(text).map { sentence ->
                SentenceUtterance(
                    locator = SentenceLocators.forSentence(locator, sentence.start, sentence.end),
                    segments = sentence.segments,
                )
            }
        }
}
