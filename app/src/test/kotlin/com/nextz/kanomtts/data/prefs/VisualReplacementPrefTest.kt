package com.nextz.kanomtts.data.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualReplacementPrefTest {
    @Test fun `setVisualReplacement writes through the interface`() = runTest {
        val prefs: ReaderPreferences = com.nextz.kanomtts.reader.util.FakeReaderPreferences()
        assertEquals(false, prefs.preferences.first().visualReplacement) // default off
        prefs.setVisualReplacement(true)
        assertEquals(true, prefs.preferences.first().visualReplacement)
    }
}
