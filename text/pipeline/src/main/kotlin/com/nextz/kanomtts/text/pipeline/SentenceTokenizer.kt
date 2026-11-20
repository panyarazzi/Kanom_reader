package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

object SentenceTokenizer {

    private const val THAI_PAIYANNOI = 'ฯ' // ฯ
    private const val THAI_MAIYAMOK = 'ๆ' // ๆ (repetition mark — binds to the preceding word)

    /** Returns non-blank sentences with absolute offsets into [input]. */
    fun tokenize(input: String): List<Sentence> {
        if (input.isBlank()) return emptyList()
        val coarse = icuSentences(input)
        val refined = mutableListOf<Sentence>()
        for (c in coarse) refined += refineThai(input, c.start, c.end)
        return refined.filter { input.substring(it.start, it.end).isNotBlank() }
    }

    private fun icuSentences(input: String): List<Sentence> {
        val bi = BreakIterator.getSentenceInstance(ULocale.forLanguageTag("th"))
        bi.setText(input)
        val out = mutableListOf<Sentence>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            out += Sentence(input.substring(start, end), start, end)
            start = end
            end = bi.next()
        }
        return out
    }

    /** ICU under-splits Thai (no sentence punctuation). Within an ICU sentence,
     *  also break after 'ฯ' and at a whitespace gap that sits *between two Thai
     *  characters* — Thai uses spaces to separate clauses, but spaces around an
     *  embedded English word (e.g. "เปิดแอป Kanom แล้ว") must NOT split the
     *  sentence. Offsets stay absolute. */
    private fun refineThai(input: String, from: Int, to: Int): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var segStart = from
        var i = from
        while (i < to) {
            val ch = input[i]
            val isBreak = when {
                ch == THAI_PAIYANNOI -> true
                ch.isWhitespace() -> {
                    val next = if (i + 1 < to && !input[i + 1].isWhitespace()) input[i + 1] else null
                    val prev = prevNonSpace(input, i - 1, segStart)
                    // Don't break before a lone ไม้ยมก: it repeats the preceding word, so
                    // isolating it as its own sentence reads the bare mark and mis-highlights.
                    next != null && next != THAI_MAIYAMOK && prev != null && isThai(prev) && isThai(next)
                }
                else -> false
            }
            if (isBreak) {
                val end = i + 1
                out += Sentence(input.substring(segStart, end), segStart, end)
                segStart = end
            }
            i++
        }
        if (segStart < to) out += Sentence(input.substring(segStart, to), segStart, to)
        return out
    }

    private fun prevNonSpace(input: String, fromIndex: Int, lowerBound: Int): Char? {
        var j = fromIndex
        while (j >= lowerBound) {
            if (!input[j].isWhitespace()) return input[j]
            j--
        }
        return null
    }

    private fun isThai(c: Char): Boolean = scriptOf(c.code) == Script.THAI
}
