package com.nextz.kanomtts.playback.util

import com.nextz.kanomtts.playback.TtsEvent
import com.nextz.kanomtts.playback.TtsProvider
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.text.pipeline.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Test fake for [TtsProvider].
 *
 * Records each [speak] call's arguments in [calls]. By default [speak] returns immediately.
 * When [speakGate] is non-null, [speak] suspends until [releaseSpeakGate] is called.
 */
class FakeTtsProvider : TtsProvider {

    data class SpeakCall(
        val text: String,
        val lang: Lang,
        val voiceId: String?,
        val speedRate: Float,
        val pitch: Float,
        /** false = QUEUE_ADD (gapless prefetch); true = QUEUE_FLUSH. */
        val flush: Boolean = true,
    )

    val calls = mutableListOf<SpeakCall>()
    var stopCount = 0
    var shutdownCount = 0

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 64)
    override val events: Flow<TtsEvent> = _events

    // Gate: when non-null, speak() will suspend holding this mutex until released
    private var speakGate: Mutex? = null

    /**
     * Arm the gate so the NEXT speak() call suspends until [releaseSpeakGate].
     * The gate auto-locks itself, so speak() will block on the second lock acquisition.
     */
    fun armSpeakGate(): Mutex {
        val m = Mutex(locked = true)
        speakGate = m
        return m
    }

    /** Release the gate so the blocked speak() call can return. */
    fun releaseSpeakGate() {
        speakGate?.unlock()
        speakGate = null
    }

    override suspend fun speak(text: String, lang: Lang, voiceId: String?, speedRate: Float, pitch: Float) {
        val id = enqueue(text, lang, voiceId, speedRate, pitch, flush = true)
        awaitDone(id)
    }

    override fun enqueue(
        text: String,
        lang: Lang,
        voiceId: String?,
        speedRate: Float,
        pitch: Float,
        flush: Boolean,
    ): String {
        calls += SpeakCall(text, lang, voiceId, speedRate, pitch, flush)
        return (calls.size - 1).toString()
    }

    override suspend fun awaitDone(utteranceId: String) {
        // Mirrors the real provider: the speak gate parks the awaiting loop until released.
        speakGate?.withLock { }
    }

    override fun stop() {
        stopCount++
    }

    override fun shutdown() {
        shutdownCount++
    }

    override suspend fun voices(lang: Lang): List<Voice> = emptyList()
}
