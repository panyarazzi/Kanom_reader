package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

/** Audition a voice with a fixed sample phrase; pauses active playback and stays paused. */
fun interface VoicePreviewer {
    suspend fun preview(lang: Lang, voiceId: String?, speed: Float, pitch: Float)
}
