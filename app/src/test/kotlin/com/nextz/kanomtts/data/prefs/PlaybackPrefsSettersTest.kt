package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.playback.util.FakeReaderPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPrefsSettersTest {
    @Test fun `playback setters write through the interface`() = runTest {
        val prefs: ReaderPreferences = FakeReaderPreferences()
        prefs.setSkipUrls(true)
        prefs.setHighlightOn(false)
        prefs.setAutoScroll(false)
        prefs.setSleepTimerDefaultMin(45)
        val p = prefs.preferences.first()
        assertEquals(true, p.skipUrls)
        assertEquals(false, p.highlightOn)
        assertEquals(false, p.autoScroll)
        assertEquals(45, p.sleepTimerDefaultMin)
    }
}
