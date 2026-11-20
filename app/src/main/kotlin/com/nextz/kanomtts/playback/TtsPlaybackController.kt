package com.nextz.kanomtts.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.data.repository.ProgressReader
import com.nextz.kanomtts.data.repository.ProgressWriter
import com.nextz.kanomtts.playback.di.PlaybackScope
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.SpeechSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central TTS playback state machine.
 *
 * All mutable state lives on [scope]'s dispatcher. Public methods are safe to
 * call from any coroutine context — they enqueue work on [scope] via launch.
 *
 * Speak-loop design: [play] launches a single Job ([playJob]) that owns the
 * speak loop. No nested launches — avoids scheduling surprises under the
 * StandardTestDispatcher used in unit tests.
 *
 * Cursor semantics: starts at 0 after [start]. Advances by one after each
 * utterance is spoken. [nextSentence]/[previousSentence]/[jumpToSegment] cancel
 * [playJob], move the cursor, then restore context via [seek] — relaunching the
 * speak loop when a session was playing (skip-and-continue) or refreshing the
 * paused Now-Playing display otherwise.
 *
 * Lazy streaming: [start] launches a [streamJob] that appends to [utterances]
 * as the source emits. The play loop waits for more data when it catches up
 * with the stream by suspending on a combination of [loadedCount] and
 * [streamDone] — it wakes when either a new utterance arrives (loadedCount++)
 * or the stream completes ([streamDone] flips false→true), then the loop ends.
 * Both are [MutableStateFlow]s so the wake-up is a real (distinct) emission and
 * is thread-safe.
 *
 * ttsLocator persistence: on each sentence advance the current locator is saved
 * to the ttsLocator column via [progressWriter.saveTtsLocator] (partial-column
 * UPDATE — does NOT overwrite the reading locator). A 1-second debounce prevents
 * per-sentence DB writes. On [start] with a null fromLocator, the saved ttsLocator
 * is decoded and used as the start position.
 *
 * Sleep timer: [sleepTimer] is armed via [setSleepTimer]. Expirations trigger
 * [pause]. Chapter-boundary crossing in the play loop calls
 * [SleepTimer.onChapterBoundary] for end-of-chapter mode.
 * [sleepRemaining] and [sleepFade] are exposed for the mini-player (Task 10).
 *
 * Audio fade deferral: [SleepTimer.fadeFraction] is the UX signal for
 * mini-player dimming. Actual audio volume attenuation is deferred until
 * [TtsProvider.speak] gains a volume parameter (out of scope for Task 7).
 *
 * Service lifecycle: [startAndPlay]/[play] connect a [MediaController] to bind
 * the [TtsPlaybackService] (a [MediaSessionService]). Binding avoids the manual
 * startForegroundService 5s-contract: media3 calls startForeground() itself only
 * while the player is actually playing and demotes when it stops.
 * [stop]/[tearDown] release the controller future; when no client is bound and
 * the player is idle, media3's MediaSessionService stops itself.
 */
@OptIn(FlowPreview::class)
@Singleton
class TtsPlaybackController @Inject constructor(
    private val provider: TtsProvider,
    private val contentSource: TtsContentSource,
    private val voiceConfig: VoiceConfigSupplier,
    private val progressReader: ProgressReader,
    private val progressWriter: ProgressWriter,
    private val readerPreferences: ReaderPreferences,
    @PlaybackScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val audioFocus: AudioFocus,
) : TtsPlaybackSource, VoicePreviewer, PlaybackStopper {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    override val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    /** Errors / missing-voice events surfaced from provider events. Null when clear. */
    private val _providerError = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val providerError = _providerError.asSharedFlow()

    // ── sleep timer ───────────────────────────────────────────────────────────

    internal val sleepTimer = SleepTimer(scope)

    /** Remaining ms on the countdown; null when inactive. Exposed for mini-player. */
    val sleepRemaining: StateFlow<Long?> = sleepTimer.remaining

    /** Fade fraction 1f→0f over the last FADE_MS before expiry. For mini-player dimming. */
    val sleepFade: StateFlow<Float> = sleepTimer.fadeFraction

    /** Language of the segment currently being spoken; null when idle/paused. */
    private val _currentLang = MutableStateFlow<Lang?>(null)
    val currentLang: StateFlow<Lang?> = _currentLang.asStateFlow()

    /** Text of the sentence currently being spoken; null when idle/stopped. Drives the Now-Playing card. */
    private val _currentSentenceText = MutableStateFlow<String?>(null)
    val currentSentenceText: StateFlow<String?> = _currentSentenceText.asStateFlow()

    // ── tts locator debounce channel ─────────────────────────────────────────
    // Buffers at most 1 pending save; drops oldest on overflow so only the
    // latest locator is persisted after the 1-second quiet window.

    private val _ttsLocatorSave = MutableSharedFlow<Pair<String, Locator>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // Wire AudioFocus callbacks — avoids a circular Hilt constructor dependency
        // (AudioFocus no longer injects the controller; we push callbacks instead). (I1)
        audioFocus.bind(
            onPause = { pause() },
            onPlay = { play() },
            onStop = { stop() },
        )

        // Collect sleep-timer expirations and pause playback.
        scope.launch {
            sleepTimer.expirations.collect { pause() }
        }

        // Collect provider events — surface missing-voice / errors for the UI.
        scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is TtsEvent.MissingVoice -> {
                        val msg = "Missing TTS voice for language: ${event.lang}"
                        Log.w("TtsPlaybackController", msg)
                        _providerError.tryEmit(msg)
                    }
                    is TtsEvent.Error -> {
                        val msg = "TTS engine error (code ${event.code}) on utterance ${event.utteranceId}"
                        Log.w("TtsPlaybackController", msg)
                        _providerError.tryEmit(msg)
                    }
                    else -> Unit
                }
            }
        }

        // Debounced tts locator persistence (mirrors reader's 800ms debounce).
        scope.launch {
            _ttsLocatorSave
                .debounce(1_000L)
                .collect { (bookId, locator) ->
                    val now = System.currentTimeMillis()
                    progressWriter.saveTtsLocator(
                        bookId = bookId,
                        ttsLocator = LocatorSerializer.encode(locator),
                        updatedAt = now,
                    )
                }
        }

        // Keep the cached config map live so voice/speed/pitch edits apply at the next segment.
        scope.launch { voiceConfig.observe().collect { configs = it } }
    }

    // ── streaming internals ───────────────────────────────────────────────────

    private val utterances = mutableListOf<SentenceUtterance>()
    private val loadedCount = MutableStateFlow(0)
    private val streamDone = MutableStateFlow(false)

    /** Exposed for testing only — allows assertions on prefetch window size. */
    internal val loadedCountForTest: Int get() = loadedCount.value

    private var configs: Map<String, VoiceConfigEntity> = emptyMap()
    // Observable cursor — StateFlow lets the backpressure predicate re-evaluate on every advance.
    private val cursor = MutableStateFlow(0)
    private var currentBookId: String? = null
    private var playJob: Job? = null
    private var streamJob: Job? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    // Transport intent + supersession guard — race-safety for seek vs pause/stop/play.
    //
    // wantPlaying: the user's CURRENT intent. Set true by play()/startAndPlay(), false by
    //   pause()/stop()/start(). A seek() suspends inside cancelPlay()'s playJob.join(); while
    //   suspended, a queued pause()/stop() runs to completion on this single-threaded dispatcher.
    //   Reading wantPlaying AFTER the join (instead of snapshotting "was playing" before it) means
    //   seek() relaunches only if the user still wants playback — fixing the lost-pause race.
    // commandEpoch: bumped by every transport command. A seek() that finds the epoch changed after
    //   its join knows a newer command (another seek, a pause/stop) superseded it and must not relaunch
    //   — prevents two interleaved seeks from each launching a loop (orphaned, uncancellable playJob).
    private var wantPlaying = false
    private var commandEpoch = 0L

    // ── public API ────────────────────────────────────────────────────────────

    override fun start(bookId: String, fromLocator: Locator?, fromSelection: Boolean) {
        scope.launch {
            wantPlaying = false
            commandEpoch++
            cancelPlay()
            streamJob?.cancel()
            streamJob?.join()
            streamJob = null

            utterances.clear()
            loadedCount.value = 0
            streamDone.value = false
            cursor.value = 0
            currentBookId = bookId
            _state.value = PlaybackState.Loading
            _currentLocator.value = null
            _currentSentenceText.value = null

            configs = voiceConfig.load()

            // Resolve start locator: use provided one or resume from saved ttsLocator
            val resolvedLocator = fromLocator
                ?: LocatorSerializer.decode(progressReader.get(bookId)?.ttsLocator)

            // Launch streaming job — appends utterances lazily
            streamJob = scope.launch {
                try {
                    contentSource.stream(bookId, resolvedLocator, skipToSelection = fromSelection).collect { utterance ->
                        utterances.add(utterance)
                        loadedCount.value = utterances.size
                        // Backpressure: don't prefetch/parse more than AHEAD_BUFFER ahead of the
                        // speaking cursor — bounds memory + stops parsing the whole book up front (OOM fix).
                        cursor.first { loadedCount.value - it <= AHEAD_BUFFER }
                    }
                } finally {
                    // false→true emits (distinct), waking any suspended play loop.
                    streamDone.value = true
                }
            }
        }
    }

    /**
     * Atomically set up streaming AND launch the speak loop in a single scope.launch.
     *
     * Eliminates the start()+play() race: the caller's thread sees [currentBookId] == null
     * until the controller's scope.launch executes, so a plain play() called immediately
     * after start() bails out with no-op. [startAndPlay] puts setup + loop in one block
     * so ordering is guaranteed — this is the fix for the "click play, nothing happens" bug.
     *
     * Use for initial-start triggers. Use [play]/[pause] for resume after pause.
     */
    override fun startAndPlay(bookId: String, fromLocator: Locator?, fromSelection: Boolean) {
        // The ENTIRE setup + speak loop runs inside the coroutine tracked as [playJob]
        // so that cancelPlay()/pause()/stop() reliably stop a session started via
        // startAndPlay — exactly like the standalone play() tracks its loop. (BUG 2)
        //
        // Race: if pause() arrives while this is still in setup (before any speak),
        // pause()'s cancelPlay() cancels THIS job (now assigned synchronously below),
        // so the loop never runs and currentBookId — set early below — lets pause emit
        // the correct state.
        wantPlaying = true
        commandEpoch++
        val previousPlayJob = playJob  // capture so we can join it without self-deadlock
        playJob = scope.launch {
            // ── setup (same as start()) ──────────────────────────────────────
            previousPlayJob?.cancel()
            previousPlayJob?.join()  // fully tear down any prior session's loop first
            streamJob?.cancel()
            streamJob?.join()
            streamJob = null

            utterances.clear()
            loadedCount.value = 0
            streamDone.value = false
            cursor.value = 0
            currentBookId = bookId
            _state.value = PlaybackState.Loading
            _currentLocator.value = null
            _currentSentenceText.value = null

            configs = voiceConfig.load()

            val resolvedLocator = fromLocator
                ?: LocatorSerializer.decode(progressReader.get(bookId)?.ttsLocator)

            streamJob = scope.launch {
                try {
                    contentSource.stream(bookId, resolvedLocator, skipToSelection = fromSelection).collect { utterance ->
                        utterances.add(utterance)
                        loadedCount.value = utterances.size
                        // Backpressure: don't prefetch/parse more than AHEAD_BUFFER ahead of the
                        // speaking cursor — bounds memory + stops parsing the whole book up front (OOM fix).
                        cursor.first { loadedCount.value - it <= AHEAD_BUFFER }
                    }
                } finally {
                    streamDone.value = true
                }
            }

            // ── speak loop (same as play()) — guaranteed after setup ─────────
            startServiceIfNeeded()
            runSpeakLoop(bookId)
        }
    }

    override fun play() {
        wantPlaying = true
        commandEpoch++
        if (playJob?.isActive == true) return
        val bId = currentBookId ?: return

        startServiceIfNeeded()

        playJob = scope.launch {
            runSpeakLoop(bId)
        }
    }

    override fun pause() {
        scope.launch {
            wantPlaying = false
            commandEpoch++
            val bId = currentBookId ?: return@launch
            val loc = _currentLocator.value
            cancelPlay()
            _state.value = if (loc != null) {
                PlaybackState.Paused(bId, loc)
            } else {
                PlaybackState.Idle
            }
        }
    }

    override fun stop() {
        scope.launch { tearDown() }
    }

    /**
     * Tears down playback and releases the underlying TTS engine.
     *
     * Note: does NOT call provider.shutdown() because SystemTtsProvider wraps a
     * @Singleton TextToSpeech — shutting it down permanently kills the engine for the
     * process lifetime. Only stop() (pause the engine) here; the engine is kept alive
     * for reuse. (M4)
     */
    fun shutdown() {
        scope.launch {
            tearDown()
            // provider.shutdown() intentionally omitted — would permanently destroy the
            // singleton TextToSpeech. TtsPlayer.handleRelease calls this; the engine
            // must survive a release/rebind cycle.
        }
    }

    /** Cancels jobs, stops the provider, and resets all playback state to Idle. */
    private suspend fun tearDown() {
        wantPlaying = false
        commandEpoch++
        cancelPlay()
        streamJob?.cancel()
        streamJob?.join()
        streamJob = null

        provider.stop()
        sleepTimer.cancel()
        utterances.clear()
        loadedCount.value = 0
        streamDone.value = false
        cursor.value = 0
        currentBookId = null
        _state.value = PlaybackState.Idle
        _currentLocator.value = null
        _currentLang.value = null
        _currentSentenceText.value = null
        stopService()
    }

    fun nextSentence() = seek { c ->
        // Once the stream is complete, the last valid cursor is the last utterance (size - 1).
        // Advancing to size would park the loop past the end → it exits → a dead Play button.
        // While still streaming, advancing to size is legal: the loop waits for more to load.
        val maxCursor = if (streamDone.value) utterances.size - 1 else utterances.size
        if (c < maxCursor) c + 1 else c
    }

    fun previousSentence() = seek { if (it > 0) it - 1 else it }

    fun jumpToSegment(i: Int) = seek { i }

    /**
     * Moves the play cursor, then restores playback context.
     *
     * The seek operations ([nextSentence]/[previousSentence]/[jumpToSegment]) all
     * cancel the speak loop before touching the cursor. The fix for the
     * "Next/Previous does nothing" bug is what happens AFTER the cursor moves:
     *  - if the user still wants playback ([wantPlaying]), the speak loop is
     *    relaunched from the new cursor so playback skips-and-continues;
     *  - if paused, the Now-Playing display (sentence text + locator) is refreshed
     *    to the new cursor so the skip is visible, while staying paused.
     *
     * Race-safety: [cancelPlay] suspends on the running loop's join; a pause/stop/seek
     * queued during that window runs first. We re-read [commandEpoch] after the join and
     * bail if superseded, and gate the relaunch on the LATEST [wantPlaying] — so an
     * interleaved pause is never lost and two fast taps never launch two loops.
     *
     * Callers that drive their own play() afterwards (an initial jump before playback,
     * or the start()→seek()→play() unit-test sequence) leave [wantPlaying] false and the
     * state non-Paused, so only the cursor moves and play() picks it up.
     *
     * @param newCursor maps the current cursor to the desired one; the result is
     *   coerced into 0..utterances.size.
     */
    private fun seek(newCursor: (Int) -> Int) {
        scope.launch {
            val myEpoch = ++commandEpoch
            cancelPlay()  // suspends on playJob.join(); a queued pause/stop/seek may run during it
            cursor.value = newCursor(cursor.value).coerceIn(0, utterances.size)
            // If a newer transport command arrived while we were joining, it owns the outcome now.
            if (commandEpoch != myEpoch) return@launch
            val bId = currentBookId ?: return@launch
            when {
                // Skip-and-continue: relaunch the loop only if the user still wants playback
                // (a pause/stop during the join above would have cleared wantPlaying).
                wantPlaying -> {
                    startServiceIfNeeded()
                    playJob = scope.launch { runSpeakLoop(bId) }
                }
                // Skip-while-paused: refresh the Now-Playing display to the new cursor, stay paused.
                _state.value is PlaybackState.Paused -> {
                    val idx = cursor.value
                    if (idx in utterances.indices) {
                        val utterance = utterances[idx]
                        _currentLocator.value = utterance.locator
                        _currentSentenceText.value =
                            PlaybackFormatting.sentenceTextOf(utterance.segments)
                        _state.value = PlaybackState.Paused(bId, utterance.locator)
                    }
                }
            }
        }
    }

    /**
     * Arms the sleep timer.
     *
     * @param durationMin minutes to count down; null uses the default from
     *   [ReaderPreferences] (only when [endOfChapter] is also false).
     * @param endOfChapter when true, expires on the next chapter boundary.
     */
    fun setSleepTimer(durationMin: Int?, endOfChapter: Boolean = false) {
        scope.launch {
            val durationMs = when {
                durationMin != null -> durationMin * 60_000L
                !endOfChapter -> {
                    val prefs = readerPreferences.preferences.first()
                    prefs.sleepTimerDefaultMin * 60_000L
                }
                else -> null  // end-of-chapter mode — no countdown
            }
            sleepTimer.start(durationMs, endOfChapter)
        }
    }

    override suspend fun preview(lang: Lang, voiceId: String?, speed: Float, pitch: Float) {
        // Free the single TextToSpeech: pause any active session and wait for the loop to stop.
        if (_state.value is PlaybackState.Playing) {
            pause()
            playJob?.join()
        }
        try {
            provider.speak(
                text = VoiceSamples.sampleFor(lang),
                lang = lang,
                voiceId = voiceId,
                speedRate = PlaybackFormatting.clampSpeed(speed),
                pitch = PlaybackFormatting.clampPitch(pitch),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _providerError.tryEmit("Preview failed: ${e.message}")
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    /**
     * Core speak loop — shared between [play] and [startAndPlay].
     *
     * Gapless pipelining: each segment is [TtsProvider.enqueue]d, then BEFORE awaiting it the loop
     * prefetches the NEXT segment with QUEUE_ADD (when it is loaded and shares the same voice
     * params), so the engine synthesises it while the current one plays — eliminating the
     * inter-sentence stall of one QUEUE_FLUSH-per-utterance. Differing-voice boundaries are not
     * pre-enqueued (engine voice/rate/pitch are global), so they keep a small gap; this is rare
     * (most sentences share one voice).
     *
     * Highlight/locator/chapter-boundary fire at the start of each sentence — which, for a
     * prefetched sentence, is right after the previous one's [TtsProvider.awaitDone], i.e. ~when
     * its audio begins. On a TTS error: emit to [providerError], flush, go [PlaybackState.Paused],
     * and break (does not leave the state stuck on Playing). (M1)
     */
    private suspend fun runSpeakLoop(bId: String) {
        var lastHref: String? = null
        // A segment already enqueued (QUEUE_ADD) one step ahead of the cursor for gapless playback.
        var prefetched: Prefetched? = null

        try {
            while (currentCoroutineContext().isActive) {
                // Wait for more utterances if cursor has caught up with the stream
                if (cursor.value >= utterances.size) {
                    if (streamDone.value) break
                    // Suspend until a new utterance arrives (loadedCount++) OR the
                    // stream completes (streamDone false→true). Combining both
                    // StateFlows guarantees a wake-up on either signal.
                    combine(loadedCount, streamDone) { count, done -> count > cursor.value || done }
                        .first { it }
                    if (cursor.value >= utterances.size) break
                }

                val uttIndex = cursor.value
                val utterance = utterances[uttIndex]
                _currentSentenceText.value = PlaybackFormatting.sentenceTextOf(utterance.segments)
                _state.value = PlaybackState.Playing(bId, utterance.locator)
                _currentLocator.value = utterance.locator

                // Detect chapter (resource) boundary by href change
                val currentHref = utterance.locator.href.toString()
                if (lastHref != null && currentHref != lastHref) {
                    sleepTimer.onChapterBoundary()
                }
                lastHref = currentHref

                for (segIndex in utterance.segments.indices) {
                    if (!currentCoroutineContext().isActive) break
                    val segment = utterance.segments[segIndex]
                    _currentLang.value = segment.lang
                    val choice = VoiceRouter.choose(segment.lang, configs)

                    // Reuse the prefetched enqueue if it is exactly this segment; else enqueue now.
                    val id: String = try {
                        prefetched?.takeIf { it.uttIndex == uttIndex && it.segIndex == segIndex }?.id
                            ?: provider.enqueue(
                                text = segment.spokenText,
                                lang = segment.lang,
                                voiceId = choice.voiceId,
                                speedRate = choice.speed,
                                pitch = choice.pitch,
                                flush = true,
                            )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handleSpeakError(bId, e)
                        return
                    }
                    prefetched = null

                    // Prefetch the next segment (QUEUE_ADD) for gapless playback — only when it is
                    // already loaded AND uses the same voice params as this one.
                    val next = nextSegment(uttIndex, segIndex)
                    if (next != null && segment.lang == next.segment.lang) {
                        val nextChoice = VoiceRouter.choose(next.segment.lang, configs)
                        if (choice == nextChoice) {
                            prefetched = try {
                                Prefetched(
                                    uttIndex = next.uttIndex,
                                    segIndex = next.segIndex,
                                    id = provider.enqueue(
                                        text = next.segment.spokenText,
                                        lang = next.segment.lang,
                                        voiceId = nextChoice.voiceId,
                                        speedRate = nextChoice.speed,
                                        pitch = nextChoice.pitch,
                                        flush = false,
                                    ),
                                )
                            } catch (e: Exception) {
                                // prefetch is best-effort; a failure just costs one small gap.
                                Log.d("TtsPlaybackController", "prefetch enqueue failed (will flush next): ${e.message}")
                                null
                            }
                        }
                    }

                    try {
                        provider.awaitDone(id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // pause/stop cancels playJob → cancels awaitDone. Propagate cleanly (BUG 3).
                        throw e
                    } catch (e: Exception) {
                        handleSpeakError(bId, e)
                        return
                    }
                }

                if (currentCoroutineContext().isActive) {
                    cursor.value++
                    // Enqueue tts locator save (debounced 1s — not per-sentence). (I2)
                    _ttsLocatorSave.tryEmit(bId to utterance.locator)
                }
            }
        } finally {
            // Cancelled mid-flight: flush so a prefetched utterance does not keep playing. (A
            // prefetch enqueued just before cancellation isn't covered by awaitDone's own flush.)
            if (!currentCoroutineContext().isActive) provider.stop()
        }

        if (currentCoroutineContext().isActive && _state.value is PlaybackState.Playing) {
            _currentLang.value = null
            _currentSentenceText.value = null
            _state.value = PlaybackState.Idle
        }
    }

    /** A segment enqueued one step ahead of the cursor (QUEUE_ADD) for gapless playback. */
    private class Prefetched(val uttIndex: Int, val segIndex: Int, val id: String)

    private class NextSegment(val uttIndex: Int, val segIndex: Int, val segment: SpeechSegment)

    /** The segment immediately after ([uttIndex], [segIndex]) if it is already loaded, else null. */
    private fun nextSegment(uttIndex: Int, segIndex: Int): NextSegment? {
        val utt = utterances.getOrNull(uttIndex) ?: return null
        if (segIndex + 1 < utt.segments.size) {
            return NextSegment(uttIndex, segIndex + 1, utt.segments[segIndex + 1])
        }
        val nextUtt = utterances.getOrNull(uttIndex + 1) ?: return null
        val firstSeg = nextUtt.segments.firstOrNull() ?: return null
        return NextSegment(uttIndex + 1, 0, firstSeg)
    }

    /** M1: surface a TTS engine error, flush any prefetch, and transition to Paused. */
    private fun handleSpeakError(bId: String, e: Exception) {
        val msg = "TTS speak failed: ${e.message}"
        Log.w("TtsPlaybackController", msg, e)
        _providerError.tryEmit(msg)
        provider.stop()  // flush any prefetched utterance so it does not keep playing
        val loc = _currentLocator.value
        _state.value = if (loc != null) PlaybackState.Paused(bId, loc) else PlaybackState.Idle
        _currentLang.value = null
        _currentSentenceText.value = null
    }

    private suspend fun cancelPlay() {
        playJob?.cancel()
        playJob?.join()
        playJob = null
    }

    // ── service lifecycle + audio focus (I1) ─────────────────────────────────

    private fun startServiceIfNeeded() {
        if (mediaControllerFuture == null) {
            try {
                val token = SessionToken(context, ComponentName(context, TtsPlaybackService::class.java))
                mediaControllerFuture = MediaController.Builder(context, token).buildAsync()
                // Connecting a MediaController BINDS the MediaSessionService (no 5s foreground
                // contract). media3 calls startForeground() itself only while the player is
                // actually playing, and demotes when it stops — media3 owns that contract correctly.
                // This avoids the manual startForegroundService FGS-did-not-start crash.
            } catch (e: Exception) {
                Log.w("TtsPlaybackController", "Could not connect MediaController", e)
            }
        }
        audioFocus.requestFocus()
    }

    private fun stopService() {
        audioFocus.abandonFocus()
        mediaControllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
            } catch (e: Exception) {
                Log.w("TtsPlaybackController", "Could not release MediaController", e)
            }
        }
        mediaControllerFuture = null
        // Do NOT call context.stopService() — releasing the last controller while
        // not playing lets media3's MediaSessionService stop itself naturally.
    }

    companion object {
        /**
         * Maximum number of utterances the streaming producer may be ahead of the
         * speaking cursor. When loadedCount - cursor > AHEAD_BUFFER the producer
         * suspends until the play loop advances the cursor, bounding peak memory
         * usage and preventing Readium from parsing the entire book up front (OOM fix).
         */
        private const val AHEAD_BUFFER = 64
    }
}
