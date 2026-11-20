package com.nextz.kanomtts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val prefs: ReaderPreferences,
) : ViewModel() {
    val uiState: StateFlow<KanomPreferences> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KanomPreferences())

    fun setSkipUrls(v: Boolean) = viewModelScope.launch { prefs.setSkipUrls(v) }
    fun setAutoScroll(v: Boolean) = viewModelScope.launch { prefs.setAutoScroll(v) }
    fun setHighlightOn(v: Boolean) = viewModelScope.launch { prefs.setHighlightOn(v) }
    fun setSleepTimerDefaultMin(v: Int) = viewModelScope.launch { prefs.setSleepTimerDefaultMin(v) }
    fun setReadingByParagraph(v: Boolean) = viewModelScope.launch {
        prefs.setReadingUnit(if (v) ReadingUnit.PARAGRAPH else ReadingUnit.SENTENCE)
    }
}
