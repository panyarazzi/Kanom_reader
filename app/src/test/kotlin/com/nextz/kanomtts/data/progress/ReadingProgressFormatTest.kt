package com.nextz.kanomtts.data.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingProgressFormatTest {
    @Test fun formatsAtLeastTwoDecimalPlaces() {
        assertEquals("12.35%", formatProgressPercent(0.123456))
        assertEquals("7.00%", formatProgressPercent(0.07))
        assertEquals("100.00%", formatProgressPercent(1.0))
    }

    @Test fun preservesSmallNonZeroProgressWhenRoundedToTwoDecimals() {
        assertEquals("0.01%", formatProgressPercent(0.0001))
    }

    @Test fun doesNotInventUnavailableOrInvalidProgress() {
        assertNull(formatProgressPercent(null))
        assertNull(formatProgressPercent(Double.NaN))
        assertNull(formatProgressPercent(Double.POSITIVE_INFINITY))
        assertNull(formatProgressPercent(-0.01))
        assertNull(formatProgressPercent(1.01))
    }
}
