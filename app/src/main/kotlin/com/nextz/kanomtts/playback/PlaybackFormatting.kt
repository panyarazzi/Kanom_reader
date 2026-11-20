package com.nextz.kanomtts.playback

import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.SpeechSegment
import java.util.Locale

/** Pure, deterministic display/clamp/order helpers for the player + voice settings UI. */
object PlaybackFormatting {
    const val SPEED_MIN = 0.5f
    const val SPEED_MAX = 3.0f
    const val PITCH_MIN = 0.5f
    const val PITCH_MAX = 2.0f

    fun clampSpeed(v: Float): Float = v.coerceIn(SPEED_MIN, SPEED_MAX)
    fun clampPitch(v: Float): Float = v.coerceIn(PITCH_MIN, PITCH_MAX)

    fun formatMultiplier(v: Float): String = "×%.1f".format(Locale.US, v)

    fun formatSleepRemaining(ms: Long?): String {
        if (ms == null) return ""
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, totalSec / 60, totalSec % 60)
    }

    fun langLabel(lang: Lang): String = when (lang) {
        Lang.TH -> "ไทย"
        Lang.EN -> "EN"
        Lang.JA -> "日本"
        Lang.ZH -> "中文"
    }

    /** Book progress 0..1 from Readium totalProgression (nullable). */
    fun progressFraction(totalProgression: Double?): Float =
        (totalProgression ?: 0.0).toFloat().coerceIn(0f, 1f)

    /** Reconstruct the spoken sentence from its per-script-run segments (un-transformed text). */
    fun sentenceTextOf(segments: List<SpeechSegment>): String =
        segments.joinToString("") { it.spokenText }

    /** Best-first: higher quality, then name. */
    fun orderVoices(voices: List<Voice>): List<Voice> =
        voices.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

    fun effectiveSpeed(config: VoiceConfigEntity?): Float = config?.speed ?: VoiceRouter.DEFAULT_SPEED
    fun effectivePitch(config: VoiceConfigEntity?): Float = config?.pitch ?: VoiceRouter.DEFAULT_PITCH
}
