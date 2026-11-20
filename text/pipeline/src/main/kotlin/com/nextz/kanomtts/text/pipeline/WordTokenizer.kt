package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

/** ICU dictionary word boundaries (Thai locale). Returns end-exclusive word [IntRange]s over [input]. */
object WordTokenizer {
    fun tokenize(input: String): List<IntRange> {
        if (input.isBlank()) return emptyList()
        val bi = BreakIterator.getWordInstance(ULocale.forLanguageTag("th"))
        bi.setText(input)
        val out = mutableListOf<IntRange>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            if (input.substring(start, end).isNotBlank()) out += start until end
            start = end
            end = bi.next()
        }
        return out
    }
}
