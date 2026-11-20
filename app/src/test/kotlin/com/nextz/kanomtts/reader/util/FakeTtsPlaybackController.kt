package com.nextz.kanomtts.reader.util

import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.playback.TtsPlaybackSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.shared.publication.Locator

/**
 * Minimal test double for [TtsPlaybackSource].
 *
 * Starts idle with [currentLocator] = null. Use [emitLocator] to drive the
 * flow from tests (e.g. to verify highlight/auto-scroll behaviour in
 * [com.nextz.kanomtts.reader.ReaderViewModel]). Use [emitState] to set the
 * playback state for [toggleTts] branching tests.
 *
 * Records [startCalls], [playCalls], [pauseCalls] for assertion.
 */
class FakeTtsPlaybackController : TtsPlaybackSource {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    override val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    // ── recorded calls ────────────────────────────────────────────────────────
    data class StartCall(val bookId: String, val fromLocator: Locator?)
    val startCalls = mutableListOf<StartCall>()
    val startAndPlayCalls = mutableListOf<StartCall>()
    var playCalls = 0; private set
    var pauseCalls = 0; private set

    // ── TtsPlaybackSource control ─────────────────────────────────────────────
    override fun start(bookId: String, fromLocator: Locator?, fromSelection: Boolean) {
        startCalls += StartCall(bookId, fromLocator)
    }

    override fun startAndPlay(bookId: String, fromLocator: Locator?, fromSelection: Boolean) {
        startAndPlayCalls += StartCall(bookId, fromLocator)
    }

    override fun play() { playCalls++ }

    override fun pause() { pauseCalls++ }

    // ── test helpers ──────────────────────────────────────────────────────────
    /** Drive [currentLocator] from test code. */
    fun emitLocator(locator: Locator?) {
        _currentLocator.value = locator
    }

    /** Drive [state] from test code. */
    fun emitState(s: PlaybackState) {
        _state.value = s
    }
}
