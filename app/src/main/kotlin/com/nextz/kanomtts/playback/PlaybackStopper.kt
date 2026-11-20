package com.nextz.kanomtts.playback

import kotlinx.coroutines.flow.StateFlow

/** Narrow seam for components that need to observe + stop playback (e.g. delete a playing book). */
interface PlaybackStopper {
    val state: StateFlow<PlaybackState>
    fun stop()
}
