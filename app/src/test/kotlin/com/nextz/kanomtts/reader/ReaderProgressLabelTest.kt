package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderProgressLabelTest {
    @Test fun formatsPageAndChapterTitle() {
        assertEquals("1/24 บทที่ 2", formatChapterProgress(1, 24, "บทที่ 2"))
    }

    @Test fun formatsPageWithoutChapterTitle() {
        assertEquals("24/24", formatChapterProgress(24, 24, "  "))
    }

    @Test fun clampsPageNumberToChapterRange() {
        assertEquals("24/24 บทสุดท้าย", formatChapterProgress(27, 24, "บทสุดท้าย"))
    }

    @Test fun hidesUntilReadiumProvidesPagination() {
        assertNull(formatChapterProgress(null, 24, "บทที่ 2"))
        assertNull(formatChapterProgress(1, 0, "บทที่ 2"))
    }
}
