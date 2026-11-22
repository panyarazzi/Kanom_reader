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
    suspend fun setProgressFontSizeSp(v: Int)
    suspend fun setTopProgressEnabled(v: Boolean)
    suspend fun setFooterProgressEnabled(v: Boolean)
    suspend fun setTopProgressLayout(left: String, center: String, right: String)
    suspend fun setFooterProgressLayout(left: String, center: String, right: String)
    suspend fun setLineHeight(v: Float)
    suspend fun setParagraphIndentEm(v: Float)
    suspend fun setParagraphSpacingEm(v: Float)
    suspend fun setMarginDp(v: Int)
    suspend fun setTopMarginDp(v: Int)
    suspend fun setBottomMarginDp(v: Int)
    suspend fun setBoldText(v: Boolean)
    suspend fun setReverseVolumeKeys(v: Boolean)
    suspend fun setUsePublisherStyles(v: Boolean)
    suspend fun setForceFont(v: Boolean)
    suspend fun setStripColors(v: Boolean)
    suspend fun setThaiWordBreak(v: Boolean)
    suspend fun setPageAnimation(v: Boolean)
    suspend fun setVisualReplacement(v: Boolean)
    suspend fun setReadingUnit(v: ReadingUnit)
    suspend fun setSkipUrls(v: Boolean)
    suspend fun setHighlightOn(v: Boolean)
    suspend fun setAutoScroll(v: Boolean)
    suspend fun setSleepTimerDefaultMin(v: Int)
}
