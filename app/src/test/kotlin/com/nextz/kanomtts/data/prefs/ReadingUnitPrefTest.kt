package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.playback.util.FakeReaderPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingUnitPrefTest {
    @Test fun `defaults to SENTENCE`() = runTest {
        assertEquals(ReadingUnit.SENTENCE, FakeReaderPreferences().preferences.first().readingUnit)
    }

    @Test fun `setReadingUnit writes through the interface`() = runTest {
        val prefs: ReaderPreferences = FakeReaderPreferences()
        prefs.setReadingUnit(ReadingUnit.PARAGRAPH)
        assertEquals(ReadingUnit.PARAGRAPH, prefs.preferences.first().readingUnit)
    }
}
