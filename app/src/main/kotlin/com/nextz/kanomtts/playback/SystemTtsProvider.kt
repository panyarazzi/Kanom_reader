package com.nextz.kanomtts.playback

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nextz.kanomtts.text.pipeline.Lang
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class SystemTtsProvider @Inject constructor(
    @ApplicationContext context: Context,
) : TtsProvider, VoiceCatalog {

    private val _events = MutableSharedFlow<TtsEvent>(extraBufferCapacity = 64)
    override val events: Flow<TtsEvent> = _events.asSharedFlow()

    // Continuations keyed by utteranceId, waiting for onDone/onError/onStop, plus a "completed"
    // map for the enqueue→awaitDone race: with pipelining, an utterance can finish (onDone) BEFORE
    // its awaitDone registers a continuation, so the result is parked here and claimed on await.
    // Both maps are guarded by [waitLock] to make register-vs-complete atomic (no lost wakeups).
    private val waitLock = Any()
    private val pending = HashMap<String, CancellableContinuation<Unit>>()
    private val completed = HashMap<String, Result<Unit>>()

    @Volatile private var ready = false

    // ── per-utterance overhead caches (gap reduction) ─────────────────────────
    // tts.voices enumerates the engine's full voice set (often IPC + hundreds of entries) and
    // isLanguageAvailable round-trips to the engine. Doing either on every sentence adds a stall
    // between utterances. Both are effectively constant for a session, so cache them: the engine's
    // installed voices / language support don't change while the app is foregrounded.
    private val resolvedVoices = ConcurrentHashMap<String, android.speech.tts.Voice>()
    private val unknownVoiceIds = ConcurrentHashMap.newKeySet<String>()
    private val languageAvailability = ConcurrentHashMap<String, Int>()

    private fun availabilityOf(locale: Locale): Int =
        languageAvailability.getOrPut(locale.toLanguageTag()) { tts.isLanguageAvailable(locale) }

    private fun resolveVoice(voiceId: String): android.speech.tts.Voice? {
        resolvedVoices[voiceId]?.let { return it }
        if (voiceId in unknownVoiceIds) return null
        val voice = tts.voices.orEmpty().firstOrNull { it.name == voiceId }
        if (voice != null) resolvedVoices[voiceId] = voice else unknownVoiceIds.add(voiceId)
        return voice
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            _events.tryEmit(TtsEvent.Started(utteranceId))
        }

        override fun onDone(utteranceId: String) {
            _events.tryEmit(TtsEvent.Done(utteranceId))
            val cont = synchronized(waitLock) {
                pending.remove(utteranceId).also { if (it == null) completed[utteranceId] = Result.success(Unit) }
            }
            cont?.resume(Unit)
        }

        @Deprecated("Delegate to onError(String, Int)")
        override fun onError(utteranceId: String) {
            onError(utteranceId, TextToSpeech.ERROR)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            _events.tryEmit(TtsEvent.Error(utteranceId, errorCode))
            val ex = TtsException(utteranceId, errorCode)
            val cont = synchronized(waitLock) {
                pending.remove(utteranceId).also { if (it == null) completed[utteranceId] = Result.failure(ex) }
            }
            cont?.resumeWithException(ex)
        }

        override fun onStop(utteranceId: String, interrupted: Boolean) {
            // A flush (QUEUE_FLUSH / tts.stop()) interrupted this utterance — cancel any waiter and
            // drop any parked result so a flushed prefetch never resolves a later awaitDone.
            val cont = synchronized(waitLock) {
                completed.remove(utteranceId)
                pending.remove(utteranceId)
            }
            cont?.cancel()
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            _events.tryEmit(TtsEvent.RangeStart(utteranceId, start, end))
        }
    }

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts.setAudioAttributes(audioAttrs)
            tts.setOnUtteranceProgressListener(progressListener)
            ready = true
        }
    }

    override suspend fun voices(lang: Lang): List<Voice> {
        if (!ready) return emptyList()
        val locale = lang.toLocale()
        return tts.voices.orEmpty()
            .filter { it.locale.language == locale.language }
            .map { v ->
                Voice(
                    id = v.name,
                    name = v.name,
                    lang = lang,
                    needsNetwork = v.isNetworkConnectionRequired,
                    quality = v.quality,
                )
            }
    }

    override suspend fun speak(
        text: String,
        lang: Lang,
        voiceId: String?,
        speedRate: Float,
        pitch: Float,
    ) {
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
        if (!ready) throw IllegalStateException("TextToSpeech not initialised")
        applyVoiceParams(lang, voiceId, speedRate, pitch)

        val utteranceId = UUID.randomUUID().toString()
        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val rc = tts.speak(text, queueMode, Bundle.EMPTY, utteranceId)
        if (rc != TextToSpeech.SUCCESS) {
            _events.tryEmit(TtsEvent.Error(utteranceId, TextToSpeech.ERROR))
            throw IllegalStateException("speak() rc=$rc")
        }
        return utteranceId
    }

    override suspend fun awaitDone(utteranceId: String) {
        suspendCancellableCoroutine { cont: CancellableContinuation<Unit> ->
            cont.invokeOnCancellation {
                synchronized(waitLock) {
                    pending.remove(utteranceId)
                    completed.remove(utteranceId)
                }
                tts.stop()
            }
            // Single atomic claim-or-register: if it already finished (incl. the onDone-before-await
            // race), resolve now; otherwise park the continuation for the callback to resume.
            val parked = synchronized(waitLock) {
                completed.remove(utteranceId) ?: run { pending[utteranceId] = cont; null }
            }
            parked?.fold({ cont.resume(Unit) }, { cont.resumeWithException(it) })
        }
    }

    /** Applies engine-global voice/rate/pitch for the given segment (cached lookups). */
    private fun applyVoiceParams(lang: Lang, voiceId: String?, speedRate: Float, pitch: Float) {
        val locale = lang.toLocale()
        // Language availability check (cached): LANG_* negatives mean not available
        if (availabilityOf(locale) < 0) {
            _events.tryEmit(TtsEvent.MissingVoice(lang))
            throw TtsException(null, TextToSpeech.ERROR)
        }
        // Voice resolution (cached): prefer explicit voiceId, fallback to locale default
        val androidVoice = voiceId?.let { resolveVoice(it) }
        if (androidVoice != null) {
            tts.setVoice(androidVoice)
        } else {
            tts.setLanguage(locale)
        }
        tts.setSpeechRate(speedRate)
        tts.setPitch(pitch)
    }

    override fun stop() {
        tts.stop()
        // Drop parked results for flushed (queued-but-not-awaited) utterances so they can't
        // resolve a future awaitDone. In-flight continuations are cancelled via onStop callback.
        synchronized(waitLock) { completed.clear() }
    }

    override fun shutdown() {
        tts.shutdown()
    }

    private fun Lang.toLocale(): Locale = when (this) {
        Lang.TH -> Locale("th", "TH")
        Lang.EN -> Locale("en", "US")
        Lang.JA -> Locale("ja", "JP")
        Lang.ZH -> Locale("zh", "CN")
    }
}

class TtsException(val utteranceId: String?, val errorCode: Int) :
    Exception("TTS error $errorCode on utterance $utteranceId")
