package com.nextz.kanomtts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.ReaderTheme
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val prefs: ReaderPreferences,
) : ViewModel() {
    val uiState: StateFlow<KanomPreferences> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KanomPreferences())


    fun setReaderTheme(v: ReaderTheme) = viewModelScope.launch { prefs.setReaderTheme(v) }
    fun setReadingFont(v: ReadingFont) = viewModelScope.launch { prefs.setReadingFont(v) }
    fun setFontSizeSp(v: Int) = viewModelScope.launch { prefs.setFontSizeSp(v.coerceIn(12, 72)) }
    fun setProgressFontSizeSp(v: Int) = viewModelScope.launch { prefs.setProgressFontSizeSp(v.coerceIn(8, 80)) }
    fun setTopProgressEnabled(v: Boolean) = viewModelScope.launch { prefs.setTopProgressEnabled(v) }
    fun setFooterProgressEnabled(v: Boolean) = viewModelScope.launch { prefs.setFooterProgressEnabled(v) }
    fun setTopProgressLayout(left: String, center: String, right: String) = viewModelScope.launch { prefs.setTopProgressLayout(left, center, right) }
    fun setFooterProgressLayout(left: String, center: String, right: String) = viewModelScope.launch { prefs.setFooterProgressLayout(left, center, right) }
    fun setLineHeight(v: Float) = viewModelScope.launch { prefs.setLineHeight(v.coerceIn(0.7f, 2.5f)) }
    fun setParagraphIndentEm(v: Float) = viewModelScope.launch { prefs.setParagraphIndentEm(v.coerceIn(0f, 3.5f)) }
    fun setParagraphSpacingEm(v: Float) = viewModelScope.launch { prefs.setParagraphSpacingEm(v.coerceIn(0f, 2f)) }
    fun setMarginDp(v: Int) = viewModelScope.launch { prefs.setMarginDp(v.coerceIn(0, 64)) }
    fun setTopMarginDp(v: Int) = viewModelScope.launch { prefs.setTopMarginDp(v.coerceIn(0, 80)) }
    fun setBottomMarginDp(v: Int) = viewModelScope.launch { prefs.setBottomMarginDp(v.coerceIn(0, 80)) }
    fun setBoldText(v: Boolean) = viewModelScope.launch { prefs.setBoldText(v) }
    fun setReverseVolumeKeys(v: Boolean) = viewModelScope.launch { prefs.setReverseVolumeKeys(v) }
    fun setTapAnywhereToNext(v: Boolean) = viewModelScope.launch { prefs.setTapAnywhereToNext(v) }
    fun adjustPreviousTouchZone(delta: Int) = viewModelScope.launch {
        val current = prefs.preferences.first().previousTouchZonePercent
        prefs.setPreviousTouchZonePercent((current + delta).coerceIn(0, 100))
    }
    fun adjustNextTouchZone(delta: Int) = adjustPreviousTouchZone(-delta)
    fun setUsePublisherStyles(v: Boolean) = viewModelScope.launch { prefs.setUsePublisherStyles(v) }
    fun setForceFont(v: Boolean) = viewModelScope.launch { prefs.setForceFont(v) }
    fun setStripColors(v: Boolean) = viewModelScope.launch { prefs.setStripColors(v) }
    fun setThaiWordBreak(v: Boolean) = viewModelScope.launch { prefs.setThaiWordBreak(v) }
    fun setPageAnimation(v: Boolean) = viewModelScope.launch { prefs.setPageAnimation(v) }
    fun setVisualReplacement(v: Boolean) = viewModelScope.launch { prefs.setVisualReplacement(v) }
    fun setSkipUrls(v: Boolean) = viewModelScope.launch { prefs.setSkipUrls(v) }
    fun setAutoScroll(v: Boolean) = viewModelScope.launch { prefs.setAutoScroll(v) }
    fun setHighlightOn(v: Boolean) = viewModelScope.launch { prefs.setHighlightOn(v) }
    fun setSleepTimerDefaultMin(v: Int) = viewModelScope.launch { prefs.setSleepTimerDefaultMin(v) }
    fun setReadingByParagraph(v: Boolean) = viewModelScope.launch {
        prefs.setReadingUnit(if (v) ReadingUnit.PARAGRAPH else ReadingUnit.SENTENCE)
    }
}
