package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    @Test fun thai_digits_become_arabic() {
        assertEquals("123", Normalizer.normalize("๑๒๓", Lang.TH))
    }

    @Test fun urls_replaced_when_skip_enabled() {
        val out = Normalizer.normalize(
            "ดูที่ https://example.com/path นะ", Lang.TH,
            NormalizerOptions(skipUrls = true)
        )
        assertEquals("ดูที่ ลิงก์ นะ", out)
    }

    @Test fun urls_kept_when_skip_disabled() {
        val input = "see https://example.com now"
        assertEquals(input, Normalizer.normalize(input, Lang.EN))
    }

    @Test fun english_abbreviation_expanded() {
        assertEquals("et cetera", Normalizer.normalize("etc.", Lang.EN))
    }
}
