package com.nextz.kanomtts.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionStartTest {

    // ── text matches ──────────────────────────────────────────────────────────

    @Test
    fun `matches when text contains highlight and elementProgression above threshold`() {
        // elem=0.5, sel=0.5, tol=0.05 → 0.5 >= 0.45 → true
        assertTrue(
            matchesSelectionStart(
                elementText = "สวัสดีครับ นี่คือย่อหน้าที่ห้า",
                elementProgression = 0.5,
                selectionHighlight = "นี่คือย่อหน้า",
                selectionProgression = 0.5,
            )
        )
    }

    @Test
    fun `matches when elementProgression equals selectionProgression exactly`() {
        assertTrue(
            matchesSelectionStart(
                elementText = "Hello world",
                elementProgression = 0.3,
                selectionHighlight = "Hello",
                selectionProgression = 0.3,
            )
        )
    }

    @Test
    fun `no match when text does not contain highlight even if progression is fine`() {
        assertFalse(
            matchesSelectionStart(
                elementText = "completely different text",
                elementProgression = 0.5,
                selectionHighlight = "selected phrase",
                selectionProgression = 0.5,
            )
        )
    }

    @Test
    fun `progression gate false when elementProgression is far below selectionProgression minus tolerance`() {
        // elem=0.1, sel=0.5, tol=0.05 → threshold=0.45 → 0.1 < 0.45 → false
        assertFalse(
            matchesSelectionStart(
                elementText = "contains selected phrase here",
                elementProgression = 0.1,
                selectionHighlight = "selected phrase",
                selectionProgression = 0.5,
                tolerance = 0.05,
            )
        )
    }

    @Test
    fun `null selectionProgression uses text-only match — true when text contains highlight`() {
        assertTrue(
            matchesSelectionStart(
                elementText = "contains the highlight text",
                elementProgression = 0.1,
                selectionHighlight = "highlight text",
                selectionProgression = null,
            )
        )
    }

    @Test
    fun `null elementProgression uses text-only match — true when text contains highlight`() {
        assertTrue(
            matchesSelectionStart(
                elementText = "contains the highlight text",
                elementProgression = null,
                selectionHighlight = "highlight text",
                selectionProgression = 0.9,
            )
        )
    }
}
