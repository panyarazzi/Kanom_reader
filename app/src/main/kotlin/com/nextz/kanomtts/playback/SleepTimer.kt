package com.nextz.kanomtts.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sleep timer for TTS playback.
 *
 * Modes:
 * - Countdown: [start] with a non-null [durationMs] → fires [expirations] when
 *   the countdown reaches zero.
 * - End-of-chapter: [start] with null [durationMs] and [endOfChapter]=true →
 *   fires [expirations] when [onChapterBoundary] is called.
 *
 * [remaining] ticks down every second while the countdown is active (null when
 * inactive or in end-of-chapter mode).
 *
 * [fadeFraction] is 1f while remaining > [FADE_MS]; ramps linearly from 1f→0f
 * over the last [FADE_MS] milliseconds; 0f at expiry. Always 1f in
 * end-of-chapter mode (no time-based fade).
 *
 * Audio volume attenuation is deferred: [fadeFraction] is exposed as a UX
 * signal (e.g. mini-player dim) but the [TtsProvider.speak] seam does not yet
 * accept a volume parameter. Expiry → [pause] (via controller).
 */
class SleepTimer(private val scope: CoroutineScope) {

    companion object {
        const val FADE_MS = 5_000L
        private const val TICK_MS = 1_000L
    }

    private val _remaining = MutableStateFlow<Long?>(null)
    val remaining: StateFlow<Long?> = _remaining.asStateFlow()

    private val _fadeFraction = MutableStateFlow(1f)
    val fadeFraction: StateFlow<Float> = _fadeFraction.asStateFlow()

    private val _expirations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expirations: SharedFlow<Unit> = _expirations.asSharedFlow()

    private var timerJob: Job? = null
    private var endOfChapterArmed = false

    /**
     * Arms the timer. Cancels any prior timer first.
     *
     * @param durationMs milliseconds to count down; null for end-of-chapter mode.
     * @param endOfChapter when true with a null [durationMs], arms end-of-chapter mode.
     */
    fun start(durationMs: Long?, endOfChapter: Boolean) {
        timerJob?.cancel()
        timerJob = null
        endOfChapterArmed = false
        _remaining.value = null
        _fadeFraction.value = 1f

        when {
            durationMs != null -> {
                endOfChapterArmed = false
                timerJob = scope.launch {
                    var left = durationMs
                    _remaining.value = left

                    while (isActive && left > 0) {
                        val tick = minOf(TICK_MS, left)
                        delay(tick)
                        left -= tick
                        _remaining.value = left
                        _fadeFraction.value = computeFade(left)
                    }

                    if (isActive) {
                        _expirations.tryEmit(Unit)
                    }
                }
            }
            endOfChapter -> {
                endOfChapterArmed = true
            }
        }
    }

    /** Disarms the timer. [remaining] → null, [fadeFraction] → 1f. */
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        endOfChapterArmed = false
        _remaining.value = null
        _fadeFraction.value = 1f
    }

    /**
     * Called by the controller when a resource (chapter) boundary is crossed.
     * If end-of-chapter mode is armed, fires [expirations] immediately.
     */
    fun onChapterBoundary() {
        if (endOfChapterArmed) {
            endOfChapterArmed = false
            _expirations.tryEmit(Unit)
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    /**
     * Returns the fade fraction for [remaining] ms left.
     * 1f while remaining > FADE_MS; linearly ramps 1f→0f over the last FADE_MS.
     */
    private fun computeFade(remaining: Long): Float {
        if (remaining >= FADE_MS) return 1f
        return (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }
}
