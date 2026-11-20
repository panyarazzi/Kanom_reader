package com.nextz.kanomtts.playback.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.playback.TtsPlaybackController
import com.nextz.kanomtts.text.pipeline.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MiniPlayerUiState(
    val visible: Boolean,
    val isPlaying: Boolean,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val voiceLabel: String?,
    val sleepRemainingMs: Long?,
    /** True when the read-by-paragraph preference is on — drives paragraph-vs-sentence transport labels. */
    val byParagraph: Boolean = false,
)

private fun Lang.toThaiLabel(): String = when (this) {
    Lang.TH -> "ไทย"
    Lang.EN -> "อังกฤษ"
    Lang.JA -> "ญี่ปุ่น"
    Lang.ZH -> "จีน"
}

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val controller: TtsPlaybackController,
    private val books: BookReader,
    private val prefs: ReaderPreferences,
) : ViewModel() {

    val uiState: StateFlow<MiniPlayerUiState> = combine(
        controller.state,
        controller.currentLang,
        controller.sleepRemaining,
        prefs.preferences,
    ) { state, lang, sleepRemaining, p ->
        val bookId = when (state) {
            is PlaybackState.Playing -> state.bookId
            is PlaybackState.Paused -> state.bookId
            else -> null
        }
        val book = bookId?.let { books.get(it) }
        MiniPlayerUiState(
            visible = state is PlaybackState.Playing || state is PlaybackState.Paused,
            isPlaying = state is PlaybackState.Playing,
            title = book?.title ?: "",
            author = book?.author,
            coverPath = book?.coverPath,
            voiceLabel = lang?.toThaiLabel(),
            sleepRemainingMs = sleepRemaining,
            byParagraph = p.readingUnit == ReadingUnit.PARAGRAPH,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MiniPlayerUiState(
            visible = false,
            isPlaying = false,
            title = "",
            author = null,
            coverPath = null,
            voiceLabel = null,
            sleepRemainingMs = null,
        ),
    )

    fun playPause() {
        if (uiState.value.isPlaying) controller.pause() else controller.play()
    }

    fun next() = controller.nextSentence()

    fun prev() = controller.previousSentence()

    fun stop() = controller.stop()

    fun setSleep(min: Int?) = controller.setSleepTimer(min)
}
