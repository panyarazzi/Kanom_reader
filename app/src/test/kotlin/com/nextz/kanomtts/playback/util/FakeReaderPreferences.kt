package com.nextz.kanomtts.playback.util

import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fake for [ReaderPreferences] used in playback unit tests. */
class FakeReaderPreferences(
    initial: KanomPreferences = KanomPreferences(),
) : ReaderPreferences {

    private val _prefs = MutableStateFlow(initial)
    override val preferences: Flow<KanomPreferences> = _prefs

    override suspend fun setReaderTheme(v: ReaderTheme) { _prefs.value = _prefs.value.copy(readerTheme = v) }
    override suspend fun setReadingFont(v: ReadingFont) { _prefs.value = _prefs.value.copy(readingFont = v) }
    override suspend fun setFontSizeSp(v: Int) { _prefs.value = _prefs.value.copy(fontSizeSp = v) }
    override suspend fun setProgressFontSizeSp(v: Int) { _prefs.value = _prefs.value.copy(progressFontSizeSp = v.coerceIn(8, 80)) }
    override suspend fun setTopProgressEnabled(v: Boolean) { _prefs.value = _prefs.value.copy(topProgressEnabled = v) }
    override suspend fun setFooterProgressEnabled(v: Boolean) { _prefs.value = _prefs.value.copy(footerProgressEnabled = v) }
    override suspend fun setTopProgressLayout(left: String, center: String, right: String) { _prefs.value = _prefs.value.copy(topProgressLeft = left, topProgressCenter = center, topProgressRight = right) }
    override suspend fun setFooterProgressLayout(left: String, center: String, right: String) { _prefs.value = _prefs.value.copy(footerProgressLeft = left, footerProgressCenter = center, footerProgressRight = right) }
    override suspend fun setLineHeight(v: Float) { _prefs.value = _prefs.value.copy(lineHeight = v) }
    override suspend fun setParagraphIndentEm(v: Float) { _prefs.value = _prefs.value.copy(paragraphIndentEm = v) }
    override suspend fun setParagraphSpacingEm(v: Float) { _prefs.value = _prefs.value.copy(paragraphSpacingEm = v) }
    override suspend fun setMarginDp(v: Int) { _prefs.value = _prefs.value.copy(marginDp = v) }
    override suspend fun setTopMarginDp(v: Int) { _prefs.value = _prefs.value.copy(topMarginDp = v) }
    override suspend fun setBottomMarginDp(v: Int) { _prefs.value = _prefs.value.copy(bottomMarginDp = v) }
    override suspend fun setBoldText(v: Boolean) { _prefs.value = _prefs.value.copy(boldText = v) }
    override suspend fun setReverseVolumeKeys(v: Boolean) { _prefs.value = _prefs.value.copy(reverseVolumeKeys = v) }
    override suspend fun setTapAnywhereToNext(v: Boolean) { _prefs.value = _prefs.value.copy(tapAnywhereToNext = v) }
    override suspend fun setUsePublisherStyles(v: Boolean) { _prefs.value = _prefs.value.copy(usePublisherStyles = v) }
    override suspend fun setForceFont(v: Boolean) { _prefs.value = _prefs.value.copy(forceFont = v) }
    override suspend fun setStripColors(v: Boolean) { _prefs.value = _prefs.value.copy(stripColors = v) }
    override suspend fun setThaiWordBreak(v: Boolean) { _prefs.value = _prefs.value.copy(thaiWordBreak = v) }
    override suspend fun setPageAnimation(v: Boolean) { _prefs.value = _prefs.value.copy(pageAnimation = v) }
    override suspend fun setVisualReplacement(v: Boolean) { _prefs.value = _prefs.value.copy(visualReplacement = v) }
    override suspend fun setReadingUnit(v: ReadingUnit) { _prefs.value = _prefs.value.copy(readingUnit = v) }
    override suspend fun setSkipUrls(v: Boolean) { _prefs.value = _prefs.value.copy(skipUrls = v) }
    override suspend fun setHighlightOn(v: Boolean) { _prefs.value = _prefs.value.copy(highlightOn = v) }
    override suspend fun setAutoScroll(v: Boolean) { _prefs.value = _prefs.value.copy(autoScroll = v) }
    override suspend fun setSleepTimerDefaultMin(v: Int) { _prefs.value = _prefs.value.copy(sleepTimerDefaultMin = v) }
}
