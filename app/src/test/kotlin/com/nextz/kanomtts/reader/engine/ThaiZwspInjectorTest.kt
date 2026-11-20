package com.nextz.kanomtts.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThaiZwspInjectorTest {
    private val ZWSP = '​'

    @Test fun inserts_zwsp_inside_thai_text_node() {
        val out = ThaiZwspInjector.inject("<p>สวัสดีตอนเช้า</p>")
        assertTrue("expected ZWSP inside Thai run", out.contains(ZWSP))
    }

    @Test fun leaves_ascii_untouched() {
        val out = ThaiZwspInjector.inject("<p>Hello Kanom</p>")
        assertFalse(out.contains(ZWSP))
    }

    @Test fun does_not_touch_tags_or_attributes() {
        val out = ThaiZwspInjector.inject("<a href=\"ก.html\">ลิงก์</a>")
        assertFalse("attribute value must not gain ZWSP", out.substringBefore('>').contains(ZWSP))
    }

    @Test fun idempotent() {
        val once = ThaiZwspInjector.inject("<p>สวัสดีตอนเช้า</p>")
        assertEquals(once, ThaiZwspInjector.inject(once))
    }

    @Test fun injectText_adds_zwsp_at_thai_word_boundaries() {
        val out = ThaiZwspInjector.injectText("สวัสดีตอนเช้า")
        assertTrue("expected ZWSP between Thai words", out.contains(ZWSP))
        // Stripping the ZWSP restores the original (no characters lost or added).
        assertEquals("สวัสดีตอนเช้า", out.replace(ZWSP.toString(), ""))
    }

    @Test fun injectText_leaves_non_thai_untouched() {
        assertEquals("Hello world", ThaiZwspInjector.injectText("Hello world"))
    }
}
