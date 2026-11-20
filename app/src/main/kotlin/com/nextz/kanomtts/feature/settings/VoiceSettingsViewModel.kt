package com.nextz.kanomtts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.playback.VoiceCatalog
import com.nextz.kanomtts.playback.VoicePreviewer
import com.nextz.kanomtts.playback.VoiceRouter
import com.nextz.kanomtts.text.pipeline.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceLangState(
    val lang: Lang,
    val label: String,
    val voices: List<Voice> = emptyList(),
    val selectedVoiceId: String? = null,
    val speed: Float = VoiceRouter.DEFAULT_SPEED,
    val pitch: Float = VoiceRouter.DEFAULT_PITCH,
)

data class VoiceSettingsUiState(val languages: List<VoiceLangState> = emptyList())

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val catalog: VoiceCatalog,
    private val repo: VoiceConfigRepository,
    private val previewer: VoicePreviewer,
) : ViewModel() {

    private val _voicesByLang = MutableStateFlow<Map<Lang, List<Voice>>>(emptyMap())

    val uiState: StateFlow<VoiceSettingsUiState> =
        combine(repo.observeAll(), _voicesByLang) { configs, voices ->
            val byLang = configs.associateBy { it.lang }
            VoiceSettingsUiState(
                languages = Lang.entries.map { lang ->
                    val cfg = byLang[lang.name]
                    VoiceLangState(
                        lang = lang,
                        label = PlaybackFormatting.langLabel(lang),
                        voices = voices[lang].orEmpty(),
                        selectedVoiceId = cfg?.voiceId,
                        speed = PlaybackFormatting.effectiveSpeed(cfg),
                        pitch = PlaybackFormatting.effectivePitch(cfg),
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettingsUiState())

    fun loadVoices(lang: Lang) = viewModelScope.launch {
        val ordered = PlaybackFormatting.orderVoices(catalog.voices(lang))
        _voicesByLang.update { it + (lang to ordered) }
    }

    fun selectVoice(lang: Lang, voiceId: String?) = upsert(lang) { it.copy(voiceId = voiceId) }
    fun setSpeed(lang: Lang, v: Float) = upsert(lang) { it.copy(speed = PlaybackFormatting.clampSpeed(v)) }
    fun setPitch(lang: Lang, v: Float) = upsert(lang) { it.copy(pitch = PlaybackFormatting.clampPitch(v)) }

    private fun upsert(lang: Lang, edit: (VoiceConfigEntity) -> VoiceConfigEntity) = viewModelScope.launch {
        val current = repo.get(lang.name) ?: VoiceConfigEntity(
            lang = lang.name, providerId = "system", voiceId = null,
            speed = VoiceRouter.DEFAULT_SPEED, pitch = VoiceRouter.DEFAULT_PITCH,
        )
        repo.set(edit(current))
    }

    fun preview(lang: Lang) = viewModelScope.launch {
        val cfg = repo.get(lang.name)
        previewer.preview(lang, cfg?.voiceId, PlaybackFormatting.effectiveSpeed(cfg), PlaybackFormatting.effectivePitch(cfg))
    }
}
