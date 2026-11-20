package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.Flow

/**
 * Testable seam for reader display preferences.
 * [PreferencesRepository] implements this interface; tests use [FakeReaderPreferences].
 */
interface ReaderPreferences {
    val preferences: Flow<KanomPreferences>
    suspend fun setReaderTheme(v: ReaderTheme)
    suspend fun setReadingFont(v: ReadingFont)
    suspend fun setFontSizeSp(v: Int)
    suspend fun setLineHeight(v: Float)
    suspend fun setMarginDp(v: Int)
    // Task 6 escape hatches
    suspend fun setUsePublisherStyles(v: Boolean)
    suspend fun setForceFont(v: Boolean)
    suspend fun setStripColors(v: Boolean)
    suspend fun setThaiWordBreak(v: Boolean)
    // Page-flip animation
    suspend fun setPageAnimation(v: Boolean)
    suspend fun setVisualReplacement(v: Boolean)
    suspend fun setReadingUnit(v: ReadingUnit)
    // Playback / app prefs (impl already exists in PreferencesRepository)
    suspend fun setSkipUrls(v: Boolean)
    suspend fun setHighlightOn(v: Boolean)
    suspend fun setAutoScroll(v: Boolean)
    suspend fun setSleepTimerDefaultMin(v: Int)
}
