package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTocWidthTest {
    @Test fun meebookG7sUsesExpandedWidth() {
        assertEquals(0.575f, readerTocWidthFraction("G7S", "Meebook"), 0f)
    }

    @Test fun largeReaderDisplayUsesExpandedWidth() {
        assertEquals(0.575f, readerTocWidthFraction("Unknown", "Unknown", 700), 0f)
    }

    @Test fun phonesUseWideDrawer() {
        assertEquals(0.82f, readerTocWidthFraction("CPH2357", "OPPO", 420), 0f)
    }
}
