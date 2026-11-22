package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTocWidthTest {
    @Test fun meebookG7sUsesHalfWidth() { assertEquals(0.50f, readerTocWidthFraction("G7S", "Meebook"), 0f) }
    @Test fun phonesUseWideDrawer() { assertEquals(0.82f, readerTocWidthFraction("CPH2357", "OPPO"), 0f) }
}
