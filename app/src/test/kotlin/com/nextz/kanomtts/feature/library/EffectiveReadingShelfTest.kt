package com.nextz.kanomtts.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveReadingShelfTest {
    private fun card(percent: Float, status: String? = null) = BookCard(
        id = "b1",
        title = "Book",
        author = null,
        coverPath = null,
        percent = percent,
        isReading = percent > 0f && percent < 0.999f,
        readingStatus = status,
    )

    @Test fun `manual unread status wins over a high saved progress percentage`() {
        assertEquals(LibraryShelf.TO_READ, card(0.97f, "TO_READ").effectiveReadingShelf())
    }

    @Test fun `manual finished status wins over incomplete saved progress`() {
        assertEquals(LibraryShelf.FINISHED, card(0.35f, "FINISHED").effectiveReadingShelf())
    }

    @Test fun `books without manual status retain progress-derived shelf behavior`() {
        assertEquals(LibraryShelf.READING, card(0.35f).effectiveReadingShelf())
        assertEquals(LibraryShelf.TO_READ, card(0f).effectiveReadingShelf())
        assertEquals(LibraryShelf.FINISHED, card(1f).effectiveReadingShelf())
    }
}
