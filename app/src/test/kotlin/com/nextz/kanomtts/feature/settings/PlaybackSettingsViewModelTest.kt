package com.nextz.kanomtts.feature.settings

import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.playback.util.FakeReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSettingsViewModelTest {

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `toggles write through to prefs`() = runTest {
        val prefs = FakeReaderPreferences()
        val vm = PlaybackSettingsViewModel(prefs)
        vm.setSkipUrls(true)
        vm.setSleepTimerDefaultMin(20)
        val p = prefs.preferences.first()
        assertEquals(true, p.skipUrls)
        assertEquals(20, p.sleepTimerDefaultMin)
    }

    @Test fun `setReadingByParagraph maps to ReadingUnit`() = runTest {
        val prefs = FakeReaderPreferences()
        val vm = PlaybackSettingsViewModel(prefs)
        vm.setReadingByParagraph(true)
        advanceUntilIdle()
        assertEquals(ReadingUnit.PARAGRAPH, prefs.preferences.first().readingUnit)
        vm.setReadingByParagraph(false)
        advanceUntilIdle()
        assertEquals(ReadingUnit.SENTENCE, prefs.preferences.first().readingUnit)
    }
}
