package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {
    @Test fun icu4j_thai_word_break_is_available() {
        val bi = BreakIterator.getWordInstance(ULocale.forLanguageTag("th"))
        bi.setText("สวัสดีครับ")
        var count = 0
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) { if (end > start) count++; start = end; end = bi.next() }
        // ICU's Thai dictionary should split "สวัสดีครับ" into more than one token.
        assertTrue("expected ICU Thai dictionary breaking to produce >1 token", count > 1)
    }
}
