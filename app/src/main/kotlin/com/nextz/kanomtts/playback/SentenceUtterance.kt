package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.SpeechSegment
import org.readium.r2.shared.publication.Locator

data class SentenceUtterance(
    val locator: Locator,
    val segments: List<SpeechSegment>,
)
