package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceTokenizerTest {
    @Test fun english_splits_on_terminators() {
        val s = SentenceTokenizer.tokenize("Hello world. How are you? I am fine!")
        assertEquals(3, s.size)
        assertEquals("Hello world.", s[0].text.trim())
        assertEquals("How are you?", s[1].text.trim())
    }

    @Test fun offsets_are_absolute_and_reconstruct() {
        val input = "First. Second."
        val s = SentenceTokenizer.tokenize(input)
        s.forEach { assertEquals(it.text, input.substring(it.start, it.end)) }
    }

    @Test fun thai_clauses_separated_by_space_become_separate_sentences() {
        // Thai uses spaces between clauses; expect a split on the space.
        val input = "เธอเปิดแอป แล้วกดเล่น"
        val s = SentenceTokenizer.tokenize(input)
        assertEquals(2, s.size)
        assertEquals("เธอเปิดแอป", s[0].text.trim())
        assertEquals("แล้วกดเล่น", s[1].text.trim())
        s.forEach { assertEquals(it.text, input.substring(it.start, it.end)) }
    }

    @Test fun embedded_english_is_not_split() {
        val input = "เปิดแอป Kanom แล้ว"
        val s = SentenceTokenizer.tokenize(input)
        assertEquals(1, s.size)
    }

    @Test fun paiyannoi_splits_sentence() {
        val input = "เป็นต้นฯ แล้วก็"
        val s = SentenceTokenizer.tokenize(input)
        assertTrue(s.size >= 2)
        assertTrue(s[0].text.endsWith("ฯ"))
    }

    @Test fun blank_input_yields_no_sentences() {
        assertTrue(SentenceTokenizer.tokenize("   \n  ").isEmpty())
    }
}
