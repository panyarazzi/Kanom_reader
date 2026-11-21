package com.nextz.kanomtts.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Restarts a one-shot timeout after reader interaction.
 * Kept separate from ViewModel state so the timing behavior can be tested without
 * constructing a Readium Publication or attaching a Navigator.
 */
internal class ReaderChromeAutoHideTimer(
    private val scope: CoroutineScope,
    private val delayMs: Long = 3_000L,
    private val onTimeout: () -> Unit,
) {
    private var job: Job? = null

    fun restart() {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            onTimeout()
            job = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
