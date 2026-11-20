package com.nextz.kanomtts.playback.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.playback.TtsPlaybackController
import com.nextz.kanomtts.playback.VoiceRouter
import com.nextz.kanomtts.text.pipeline.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

data class NowPlayingUiState(
    val present: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val author: String? = null,
    val coverPath: String? = null,
    val sentenceText: String? = null,
    val langLabel: String? = null,
    val progress: Float = 0f,
    val sleepRemainingMs: Long? = null,
    val speedDisplay: String = "×1.0",
    val pitchDisplay: String = "×1.0",
    val currentLang: Lang? = null,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val controller: TtsPlaybackController,
    private val books: BookReader,
    private val repo: VoiceConfigRepository,
) : ViewModel() {

    private data class Core(
        val state: PlaybackState, val sentence: String?, val lang: Lang?,
        val locator: Locator?, val sleep: Long?,
    )

    private val core = combine(
        controller.state, controller.currentSentenceText, controller.currentLang,
        controller.currentLocator, controller.sleepRemaining,
    ) { state, sentence, lang, locator, sleep -> Core(state, sentence, lang, locator, sleep) }

    val uiState: StateFlow<NowPlayingUiState> =
        combine(core, repo.observeAll()) { c, configs ->
            val bookId = (c.state as? PlaybackState.Playing)?.bookId
                ?: (c.state as? PlaybackState.Paused)?.bookId
            val book = bookId?.let { books.get(it) }
            val cfg = c.lang?.let { l -> configs.firstOrNull { it.lang == l.name } }
            NowPlayingUiState(
                present = bookId != null,
                isPlaying = c.state is PlaybackState.Playing,
                title = book?.title ?: "",
                author = book?.author,
                coverPath = book?.coverPath,
                sentenceText = c.sentence,
                langLabel = c.lang?.let { PlaybackFormatting.langLabel(it) },
                progress = PlaybackFormatting.progressFraction(c.locator?.locations?.totalProgression),
                sleepRemainingMs = c.sleep,
                speedDisplay = PlaybackFormatting.formatMultiplier(PlaybackFormatting.effectiveSpeed(cfg)),
                pitchDisplay = PlaybackFormatting.formatMultiplier(PlaybackFormatting.effectivePitch(cfg)),
                currentLang = c.lang,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    fun playPause() { if (uiState.value.isPlaying) controller.pause() else controller.play() }
    fun next() = controller.nextSentence()
    fun prev() = controller.previousSentence()
    fun stop() = controller.stop()
    fun setSleep(min: Int?) = controller.setSleepTimer(min)

    fun nudgeSpeed(delta: Float) = nudge { it.copy(speed = PlaybackFormatting.clampSpeed(it.speed + delta)) }
    fun nudgePitch(delta: Float) = nudge { it.copy(pitch = PlaybackFormatting.clampPitch(it.pitch + delta)) }

    private fun nudge(edit: (VoiceConfigEntity) -> VoiceConfigEntity) {
        val lang = uiState.value.currentLang ?: return
        viewModelScope.launch {
            val current = repo.get(lang.name) ?: VoiceConfigEntity(
                lang = lang.name, providerId = "system", voiceId = null,
                speed = VoiceRouter.DEFAULT_SPEED, pitch = VoiceRouter.DEFAULT_PITCH,
            )
            repo.set(edit(current))
        }
    }
}
