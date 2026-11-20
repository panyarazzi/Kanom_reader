package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

/**
 * Turns a block of mixed-language text into speakable sentences.
 *
 * Replacement and normalization are applied PER LANGUAGE-RUN (the sub-spans of a
 * sentence that belong to a single language/voice), never across a script
 * boundary. A replacement rule whose pattern spans two scripts (e.g. a Thai word
 * immediately followed by an English word) will therefore never match — author
 * rules within a single script. Offsets in the returned [SpokenSentence]s are
 * absolute character indices into [process]'s input and are never shifted by
 * replacement or normalization.
 */
class TextPipeline(
    private val primary: Lang,
    private val replacement: TextTransform = TextTransform { it },
    private val options: NormalizerOptions = NormalizerOptions(),
) {
    fun process(input: String): List<SpokenSentence> {
        val sentences = SentenceTokenizer.tokenize(input)
        return sentences.mapNotNull { sentence ->
            val runs = HanDisambiguator.assign(ScriptRunSplitter.split(sentence.text), primary)
            val segments = runs.mapNotNull { run ->
                val spoken = Normalizer.normalize(replacement.apply(run.text), run.lang, options).trim()
                if (spoken.isEmpty()) null else SpeechSegment(run.lang, spoken)
            }
            if (segments.isEmpty()) null
            else SpokenSentence(sentence.start, sentence.end, sentence.text, segments)
        }
    }

    /**
     * Like [process] but treats the WHOLE [input] as one paragraph: one [SpeechSegment] per
     * maximal same-language run (no sentence split). Replacement + normalization + mai-yamok are
     * applied per run. A run whose spoken text exceeds [maxRunChars] is split into ≤[maxRunChars]
     * same-language chunks (preferring a whitespace boundary, else a grapheme-cluster boundary)
     * so the TTS engine never rejects it.
     */
    fun processParagraph(input: String, maxRunChars: Int): List<SpeechSegment> {
        val runs = HanDisambiguator.assign(ScriptRunSplitter.split(input), primary)
        return runs.flatMap { run ->
            val spoken = Normalizer.normalize(replacement.apply(run.text), run.lang, options).trim()
            if (spoken.isEmpty()) emptyList()
            else chunk(spoken, maxRunChars).map { SpeechSegment(run.lang, it) }
        }
    }

    /**
     * Splits [text] into pieces of at most [maxChars] UTF-16 units. Prefers cutting at a space;
     * when none is available in the window (Thai/CJK have no inter-word spaces) it rounds the hard
     * cut back to the nearest grapheme-cluster boundary so a Thai base consonant is never severed
     * from its vowel/tone marks and a surrogate pair is never torn — either would be mis-pronounced
     * because each chunk is spoken as its own engine utterance. A forward-progress guard falls back
     * to a raw cut for the degenerate case of a single cluster wider than [maxChars].
     */
    private fun chunk(text: String, maxChars: Int): List<String> {
        if (maxChars <= 0 || text.length <= maxChars) return listOf(text)
        val cluster = BreakIterator.getCharacterInstance(ULocale.forLanguageTag("th"))
        cluster.setText(text)
        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + maxChars, text.length)
            if (end == text.length) { out += text.substring(start); break }
            val lastSpace = text.lastIndexOf(' ', end - 1)
            val cut = when {
                lastSpace > start -> lastSpace + 1            // cut after the last space
                cluster.isBoundary(end) -> end                // already on a cluster boundary
                else -> {
                    val b = cluster.preceding(end)            // nearest boundary before end
                    if (b > start && b != BreakIterator.DONE) b else end  // guard: always advance
                }
            }
            out += text.substring(start, cut)
            start = cut
        }
        return out
    }
}
