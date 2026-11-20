package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

sealed interface TtsEvent {
    data class Started(val utteranceId: String) : TtsEvent
    data class Done(val utteranceId: String) : TtsEvent
    data class RangeStart(val utteranceId: String, val start: Int, val end: Int) : TtsEvent
    data class Error(val utteranceId: String?, val code: Int) : TtsEvent
    data class MissingVoice(val lang: Lang) : TtsEvent
}
