package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTokenizerTest {
    @Test fun splits_thai_run_into_multiple_words() {
        // "สวัสดีตอนเช้า" (no spaces) should break into >1 dictionary word.
        val ranges = WordTokenizer.tokenize("สวัสดีตอนเช้า")
        assertTrue("expected multiple Thai words", ranges.size >= 2)
        ranges.forEach { assertTrue(it.first in 0..it.last && it.last < "สวัสดีตอนเช้า".length) }
    }
    @Test fun ranges_are_contiguous_and_cover_input() {
        val input = "สวัสดีตอนเช้า"
        val ranges = WordTokenizer.tokenize(input)
        assertEquals(0, ranges.first().first)
        assertEquals(input.length - 1, ranges.last().last)
    }
    @Test fun blank_yields_empty() { assertTrue(WordTokenizer.tokenize("   ").isEmpty()) }
    @Test fun ascii_word_is_single_range() {
        val ranges = WordTokenizer.tokenize("Kanom")
        assertEquals(1, ranges.size)
    }
}
