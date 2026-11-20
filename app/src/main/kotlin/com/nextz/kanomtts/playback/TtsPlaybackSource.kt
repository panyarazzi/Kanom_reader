package com.nextz.kanomtts.playback

import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.shared.publication.Locator

/**
 * Interface exposing the TTS-playback state signals and control methods that
 * [com.nextz.kanomtts.reader.ReaderViewModel] needs to drive sentence highlight,
 * auto-scroll, and start/pause/resume playback.
 *
 * [TtsPlaybackController] implements this interface. Tests can provide a simple fake.
 */
interface TtsPlaybackSource {
    val state: StateFlow<PlaybackState>
    val currentLocator: StateFlow<Locator?>

    /** Begin streaming from [fromLocator] (or resume from saved ttsLocator when null). */
    fun start(bookId: String, fromLocator: Locator?, fromSelection: Boolean = false)

    /**
     * Atomically start streaming AND launch the speak loop inside a single coroutine.
     *
     * Callers that do `start(bookId, loc); play()` face a race: the controller's
     * [start] enqueues setup work on its scope, but [play] reads [currentBookId]
     * synchronously — if the scope hasn't run yet, [currentBookId] is still null and
     * [play] returns immediately as a no-op (headline "click play, nothing happens" bug).
     *
     * [startAndPlay] eliminates the race: setup and the speak loop execute in the same
     * `scope.launch { }` block so ordering is guaranteed.
     *
     * Use for initial-start triggers (play-button, "อ่านจากตรงนี้").
     * Use plain [play] / [pause] for RESUME after pause and for media3 transport callbacks.
     *
     * @param fromSelection when true, the stream skips forward until it finds the paragraph
     *   that contains the selection highlight text (fixes the "reads from chapter top" bug).
     */
    fun startAndPlay(bookId: String, fromLocator: Locator?, fromSelection: Boolean = false)

    /** Start the speak loop (no-op if already playing). For RESUME after pause. */
    fun play()

    /** Pause the speak loop, preserving position. */
    fun pause()
}
