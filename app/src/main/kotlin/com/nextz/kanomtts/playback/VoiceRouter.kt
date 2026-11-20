package com.nextz.kanomtts.playback
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
data class VoiceChoice(val voiceId: String?, val speed: Float, val pitch: Float)
object VoiceRouter {
    const val DEFAULT_SPEED = 1.0f
    const val DEFAULT_PITCH = 1.0f
    fun choose(lang: Lang, configs: Map<String, VoiceConfigEntity>): VoiceChoice {
        val c = configs[lang.name]
        return VoiceChoice(c?.voiceId, c?.speed ?: DEFAULT_SPEED, c?.pitch ?: DEFAULT_PITCH)
    }
}
