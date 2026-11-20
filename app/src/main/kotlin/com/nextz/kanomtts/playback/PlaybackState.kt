package com.nextz.kanomtts.playback

import org.readium.r2.shared.publication.Locator

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data class Playing(val bookId: String, val locator: Locator) : PlaybackState
    data class Paused(val bookId: String, val locator: Locator) : PlaybackState
    data class Error(val message: String) : PlaybackState
}
