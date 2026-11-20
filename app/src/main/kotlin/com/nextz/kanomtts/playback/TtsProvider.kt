package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang
import kotlinx.coroutines.flow.Flow

interface TtsProvider {
    suspend fun voices(lang: Lang): List<Voice>

    /** Speak one single-language segment; suspends until done; throws on error/cancellation. */
    suspend fun speak(text: String, lang: Lang, voiceId: String?, speedRate: Float, pitch: Float)

    /**
     * Enqueue one single-language segment for playback and return its utterance id. Does NOT
     * suspend — pair with [awaitDone]. [flush] uses QUEUE_FLUSH (clears the engine queue first);
     * otherwise QUEUE_ADD, so the engine can synthesise this utterance while the previous one is
     * still playing (gapless). Voice/rate/pitch are engine-global and applied here, so only batch
     * (flush=false) utterances that share the same voice params. Throws on a missing voice / failure.
     */
    fun enqueue(text: String, lang: Lang, voiceId: String?, speedRate: Float, pitch: Float, flush: Boolean): String

    /**
     * Suspend until the [utteranceId] enqueued via [enqueue] finishes (returns) or fails (throws).
     * Returns immediately if it already finished. Cancellation flushes the engine queue.
     */
    suspend fun awaitDone(utteranceId: String)

    fun stop()

    fun shutdown()

    val events: Flow<TtsEvent>
}
