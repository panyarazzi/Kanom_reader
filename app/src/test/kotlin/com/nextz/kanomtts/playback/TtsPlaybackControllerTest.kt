package com.nextz.kanomtts.playback

import app.cash.turbine.test
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.playback.util.FakeProgressRepository
import com.nextz.kanomtts.playback.util.FakeReaderPreferences
import com.nextz.kanomtts.playback.util.FakeTtsContentSource
import com.nextz.kanomtts.playback.util.FakeTtsProvider
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.SpeechSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * TDD tests for [TtsPlaybackController].
 *
 * Uses Robolectric (@Config sdk=35) because [Locator] construction uses
 * org.json internally, which requires Android classes absent on the plain JVM.
 *
 * Dispatcher strategy: the controller is given a CoroutineScope backed by
 * UnconfinedTestDispatcher(testScheduler). Unconfined means scope.launch { }
 * calls execute eagerly on the calling thread up to the first suspension point,
 * so the vast majority of tests need no advanceUntilIdle().
 *
 * Pause-mid-stream tests: FakeTtsProvider.armSpeakGate() locks a Mutex so that
 * speak() suspends. play() then suspends inside the speak loop. The test calls
 * pause() next (on the same thread), which cancels the play job. Since the
 * play loop is already suspended (at the mutex) it is cancelled immediately,
 * and cancelPlay() / join() complete inline under UnconfinedTestDispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class TtsPlaybackControllerTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun locator(href: String, highlight: String = "") = Locator(
        href = Url(href)!!,
        mediaType = MediaType.XHTML,
        text = Locator.Text(highlight = highlight),
    )

    private fun cfg(lang: String, voiceId: String?, speed: Float = 1.0f, pitch: Float = 1.0f) =
        VoiceConfigEntity(lang = lang, providerId = "system", voiceId = voiceId, speed = speed, pitch = pitch)

    // ── fixtures ─────────────────────────────────────────────────────────────

    private val loc1 = locator("ch1.xhtml", "First sentence.")
    private val loc2 = locator("ch1.xhtml", "Second sentence.")
    private val loc3 = locator("ch2.xhtml", "Third sentence.")

    private val seg_th = SpeechSegment(Lang.TH, "สวัสดี")
    private val seg_en = SpeechSegment(Lang.EN, "Hello")

    private val utt1 = SentenceUtterance(loc1, listOf(seg_th))
    private val utt2 = SentenceUtterance(loc2, listOf(seg_en))
    private val utt3 = SentenceUtterance(loc3, listOf(seg_th, seg_en))

    private val voiceConfigs = mapOf(
        "TH" to cfg("TH", "th-voice-1", speed = 1.2f, pitch = 0.9f),
        "EN" to cfg("EN", "en-voice-2", speed = 1.0f, pitch = 1.0f),
    )

    // ── SUT factory ──────────────────────────────────────────────────────────

    private lateinit var provider: FakeTtsProvider
    private lateinit var progressRepo: FakeProgressRepository

    /**
     * Creates a controller with a scope backed by UnconfinedTestDispatcher so
     * that scope.launch { } runs eagerly on the calling thread.
     *
     * Uses Robolectric's application context for the [AudioFocus] (which requires a
     * Context to build AudioFocusRequest). AudioFocus.bind() is not called so focus
     * callbacks are no-ops in unit tests — we only care about playback state here.
     */
    private fun TestScope.controller(
        utterances: List<SentenceUtterance>,
        configs: Map<String, VoiceConfigEntity> = voiceConfigs,
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        contentSource: TtsContentSource? = null,
    ): TtsPlaybackController {
        provider = FakeTtsProvider()
        progressRepo = FakeProgressRepository()
        val source = contentSource ?: FakeTtsContentSource(utterances)
        val voiceConfigSupplier = object : VoiceConfigSupplier {
            override suspend fun load(): Map<String, VoiceConfigEntity> = configs
            override fun observe(): kotlinx.coroutines.flow.Flow<Map<String, VoiceConfigEntity>> =
                kotlinx.coroutines.flow.flowOf(configs)
        }
        val appContext = RuntimeEnvironment.getApplication()
        val audioFocus = AudioFocus(appContext)
        return TtsPlaybackController(
            provider = provider,
            contentSource = source,
            voiceConfig = voiceConfigSupplier,
            progressReader = progressRepo,
            progressWriter = progressRepo,
            readerPreferences = FakeReaderPreferences(),
            scope = scope,
            context = appContext,
            audioFocus = audioFocus,
        )
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `start then play speaks all segments in order with routed voice`() = runTest {
        val ctrl = controller(listOf(utt1, utt2))

        ctrl.start("book1", null)
        ctrl.play()

        assertEquals(2, provider.calls.size)

        val call0 = provider.calls[0]
        assertEquals("สวัสดี", call0.text)
        assertEquals(Lang.TH, call0.lang)
        assertEquals("th-voice-1", call0.voiceId)
        assertEquals(1.2f, call0.speedRate)
        assertEquals(0.9f, call0.pitch)

        val call1 = provider.calls[1]
        assertEquals("Hello", call1.text)
        assertEquals(Lang.EN, call1.lang)
        assertEquals("en-voice-2", call1.voiceId)
        assertEquals(1.0f, call1.speedRate)
        assertEquals(1.0f, call1.pitch)
    }

    /**
     * State-flow observation test.
     *
     * Uses StandardTestDispatcher so scope.launch { } is queued rather than
     * eager, allowing Turbine to subscribe and then see intermediate states as
     * advanceUntilIdle() drains the work step by step.
     */
    @Test
    fun `state transitions Idle then Loading on start then Playing then Idle on completion`() = runTest {
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1), scope = stdScope)

        assertEquals(PlaybackState.Idle, ctrl.state.value)

        ctrl.state.test {
            assertEquals(PlaybackState.Idle, awaitItem())  // replay of current value

            ctrl.start("book1", null)
            advanceUntilIdle()
            assertEquals(PlaybackState.Loading, awaitItem())

            ctrl.play()
            advanceUntilIdle()
            assertEquals(PlaybackState.Playing("book1", loc1), awaitItem())
            assertEquals(PlaybackState.Idle, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * currentLocator observation test.
     *
     * Uses StandardTestDispatcher so we can interleave advancing the scheduler
     * with collection, seeing every intermediate locator value.
     */
    @Test
    fun `currentLocator emits each utterances locator in turn during playback`() = runTest {
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2, utt3), scope = stdScope)

        ctrl.currentLocator.test {
            assertNull(awaitItem())  // initial value (null)

            ctrl.start("book1", null)
            advanceUntilIdle()
            // start sets null (already null, StateFlow won't re-emit same value)

            ctrl.play()
            advanceUntilIdle()

            assertEquals(loc1, awaitItem())
            assertEquals(loc2, awaitItem())
            assertEquals(loc3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Pause mid-stream using the speak gate.
     *
     * The gate locks provider.speak() so the play loop suspends on utt1.
     * pause() is then called synchronously, which cancels the play job.
     * Because the scope is Unconfined, cancelPlay() runs inline and join()
     * returns immediately after cancellation.
     */
    @Test
    fun `pause while speaking first utterance stops loop and state becomes Paused`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)

        // Arm gate: the FIRST speak() call will suspend
        provider.armSpeakGate()

        // play() launches the speak loop which suspends at provider.speak() for utt1
        ctrl.play()

        // At this point the speak loop is suspended; loc1 was set before speak()
        // Pause while suspended
        ctrl.pause()

        val st = ctrl.state.value
        assert(st is PlaybackState.Paused) { "Expected Paused, got $st" }

        // Only 1 call was made (utt1's segment was started but loop was blocked/cancelled)
        assertEquals(1, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)
    }

    @Test
    fun `play after pause resumes from the same sentence (re-speaks it)`() = runTest {
        val ctrl = controller(listOf(utt1, utt2))

        ctrl.start("book1", null)

        // Arm gate so first speak() blocks
        provider.armSpeakGate()
        ctrl.play()  // starts loop; suspends at utt1.speak()

        // Pause — loop cancelled at utt1; cursor stays at 0 (utt1 not complete)
        ctrl.pause()
        provider.releaseSpeakGate()

        // Clear the call from the blocked speak
        provider.calls.clear()

        // Resume — should restart from cursor=0 (utt1)
        ctrl.play()

        assertEquals(2, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)  // utt1 re-spoken
        assertEquals("Hello", provider.calls[1].text)    // utt2 spoken next
    }

    @Test
    fun `nextSentence advances cursor so play starts from the next utterance`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)
        ctrl.nextSentence()  // cursor: 0 → 1

        ctrl.play()  // play from utt2

        assertEquals("Hello", provider.calls[0].text)
        assertEquals(Lang.EN, provider.calls[0].lang)
    }

    @Test
    fun `previousSentence moves cursor back so play starts from prior utterance`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)
        ctrl.nextSentence()   // cursor: 0 → 1
        ctrl.nextSentence()   // cursor: 1 → 2
        ctrl.previousSentence()  // cursor: 2 → 1

        ctrl.play()  // play from utt2 (index 1)

        assertEquals("Hello", provider.calls[0].text)
        assertEquals(Lang.EN, provider.calls[0].lang)
    }

    @Test
    fun `nextSentence while playing skips to next utterance and keeps playing`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)

        // Park the speak loop mid-utt1 so a session is actively playing.
        provider.armSpeakGate()
        ctrl.play()  // speaks utt1, blocks at gate; cursor still 0

        // Skip while playing — must cancel utt1, advance to utt2, and CONTINUE playing.
        ctrl.nextSentence()
        provider.releaseSpeakGate()
        advanceUntilIdle()

        // utt1 (cut off) then the loop continued from utt2 → utt3 to completion.
        assertEquals(
            listOf("สวัสดี", "Hello", "สวัสดี", "Hello"),
            provider.calls.map { it.text },
        )
        assertEquals(PlaybackState.Idle, ctrl.state.value)
    }

    @Test
    fun `previousSentence while playing skips back and keeps playing`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)
        ctrl.jumpToSegment(2)  // start positioned at utt3 (index 2)

        provider.armSpeakGate()
        ctrl.play()  // speaks utt3 (seg_th), blocks at gate; cursor 2

        ctrl.previousSentence()  // back to utt2, continue playing
        provider.releaseSpeakGate()
        advanceUntilIdle()

        // utt3.seg_th (cut off) then continued from utt2 → utt3.
        assertEquals(
            listOf("สวัสดี", "Hello", "สวัสดี", "Hello"),
            provider.calls.map { it.text },
        )
        assertEquals(PlaybackState.Idle, ctrl.state.value)
    }

    @Test
    fun `nextSentence while paused updates the displayed sentence and stays paused`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)

        // Get into Paused at utt1.
        provider.armSpeakGate()
        ctrl.play()
        ctrl.pause()
        provider.releaseSpeakGate()
        assertTrue("precondition: paused", ctrl.state.value is PlaybackState.Paused)

        ctrl.nextSentence()  // paused → move to utt2, update display, stay paused

        assertEquals(loc2, ctrl.currentLocator.value)
        assertEquals("Hello", ctrl.currentSentenceText.value)
        val st = ctrl.state.value
        assertTrue("expected Paused, got $st", st is PlaybackState.Paused)
        assertEquals(loc2, (st as PlaybackState.Paused).locator)
    }

    @Test
    fun `next then pause keeps playback paused and does not resurrect`() = runTest {
        // Regression for the seek-vs-pause race: a pause queued while nextSentence() is
        // suspended in cancelPlay().join() must not be lost (playback was resurrecting).
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2, utt3), scope = stdScope)

        ctrl.start("book1", null)
        advanceUntilIdle()
        provider.armSpeakGate()
        ctrl.play()
        advanceUntilIdle()  // loop parked mid-utt1
        assertTrue(ctrl.state.value is PlaybackState.Playing)

        ctrl.nextSentence()  // queued
        ctrl.pause()         // queued — must win
        advanceUntilIdle()
        provider.releaseSpeakGate()
        advanceUntilIdle()

        val st = ctrl.state.value
        assertTrue("expected Paused after next+pause, got $st", st is PlaybackState.Paused)
    }

    @Test
    fun `double-tap next then pause leaves no orphan loop`() = runTest {
        // Two interleaved seeks must not each launch a loop (orphaned, uncancellable playJob).
        // If an orphan survived, pause() couldn't stop it and state would not settle on Paused.
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2, utt3), scope = stdScope)

        ctrl.start("book1", null)
        advanceUntilIdle()
        provider.armSpeakGate()
        ctrl.play()
        advanceUntilIdle()

        ctrl.nextSentence()
        ctrl.nextSentence()
        ctrl.pause()
        advanceUntilIdle()
        provider.releaseSpeakGate()
        advanceUntilIdle()

        val st = ctrl.state.value
        assertTrue("expected Paused (no orphan loop), got $st", st is PlaybackState.Paused)
    }

    @Test
    fun `nextSentence clamps at end of stream so play still speaks the last sentence`() = runTest {
        val ctrl = controller(listOf(utt1, utt2))

        ctrl.start("book1", null)
        advanceUntilIdle()      // streamDone == true, 2 utterances loaded
        ctrl.jumpToSegment(1)   // position on the last sentence
        ctrl.nextSentence()     // must NOT advance past the end (old bug stranded cursor at size)
        ctrl.play()
        advanceUntilIdle()

        // The last sentence still plays — the cursor wasn't stranded past the end.
        assertEquals(listOf("Hello"), provider.calls.map { it.text })
    }

    @Test
    fun `paused then next then play resumes from the new cursor`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)
        provider.armSpeakGate()
        ctrl.play()
        ctrl.pause()
        provider.releaseSpeakGate()
        assertTrue("precondition: paused", ctrl.state.value is PlaybackState.Paused)

        ctrl.nextSentence()      // paused: move to utt2
        provider.calls.clear()
        ctrl.play()              // resume — must start from the moved cursor (utt2)
        advanceUntilIdle()

        assertEquals("Hello", provider.calls.first().text)
    }

    @Test
    fun `voice routing sends TH voice config for TH segment, EN config for EN segment`() = runTest {
        val ctrl = controller(listOf(utt3))  // utt3 has [seg_th, seg_en]

        ctrl.start("book1", null)
        ctrl.play()

        assertEquals(2, provider.calls.size)

        // TH segment → TH voice config
        assertEquals("th-voice-1", provider.calls[0].voiceId)
        assertEquals(1.2f, provider.calls[0].speedRate)
        assertEquals(0.9f, provider.calls[0].pitch)

        // EN segment → EN voice config
        assertEquals("en-voice-2", provider.calls[1].voiceId)
        assertEquals(1.0f, provider.calls[1].speedRate)
        assertEquals(1.0f, provider.calls[1].pitch)
    }

    @Test
    fun `stop resets state to Idle, clears locator, and delegates to provider stop`() = runTest {
        val ctrl = controller(listOf(utt1, utt2))

        ctrl.start("book1", null)
        ctrl.stop()

        assertEquals(PlaybackState.Idle, ctrl.state.value)
        assertEquals(1, provider.stopCount)
        assertNull(ctrl.currentLocator.value)
    }

    @Test
    fun `jumpToSegment sets cursor so play begins at that index`() = runTest {
        val ctrl = controller(listOf(utt1, utt2, utt3))

        ctrl.start("book1", null)
        ctrl.jumpToSegment(2)  // jump to utt3 (index 2)
        ctrl.play()

        // Only utt3's two segments should have been spoken
        assertEquals(2, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)
        assertEquals("Hello", provider.calls[1].text)
    }

    // ── gapless pipelining (QUEUE_ADD prefetch) ───────────────────────────────

    @Test
    fun `consecutive same-voice sentences are prefetched with QUEUE_ADD for gapless playback`() = runTest {
        val a = SentenceUtterance(loc1, listOf(SpeechSegment(Lang.TH, "หนึ่ง")))
        val b = SentenceUtterance(loc2, listOf(SpeechSegment(Lang.TH, "สอง")))
        val ctrl = controller(listOf(a, b))

        ctrl.start("book1", null)
        ctrl.play()
        advanceUntilIdle()

        assertEquals(listOf("หนึ่ง", "สอง"), provider.calls.map { it.text })
        // First clears the queue; the second is enqueued QUEUE_ADD (prefetched while the first plays).
        assertEquals(true, provider.calls[0].flush)
        assertEquals(false, provider.calls[1].flush)
    }

    @Test
    fun `different-voice sentences are not batched - each flushes`() = runTest {
        // utt1 is TH, utt2 is EN — different voice params, so the EN one must NOT be pre-enqueued
        // behind the TH one (engine voice/rate/pitch are global).
        val ctrl = controller(listOf(utt1, utt2))

        ctrl.start("book1", null)
        ctrl.play()
        advanceUntilIdle()

        assertEquals(listOf("สวัสดี", "Hello"), provider.calls.map { it.text })
        assertEquals(true, provider.calls[0].flush)
        assertEquals(true, provider.calls[1].flush)
    }

    // ── lazy-consumption test ─────────────────────────────────────────────────

    /**
     * Proves lazy streaming: the controller speaks utt1 BEFORE utt2 is emitted.
     *
     * A [Channel]-based content source emits utt1 immediately, then suspends
     * waiting for a signal before emitting utt2. We verify that utt1 is spoken
     * before the signal is sent, confirming the play loop started without
     * waiting for the full book to be streamed.
     *
     * Uses StandardTestDispatcher to control scheduling precisely.
     */
    @Test
    fun `play begins before stream completes - lazy consumption`() = runTest {
        val gate = Channel<Unit>(Channel.RENDEZVOUS)

        val lazySource = object : TtsContentSource {
            override fun stream(bookId: String, fromLocator: Locator?, skipToSelection: Boolean): Flow<SentenceUtterance> =
                flow {
                    emit(utt1)          // utt1 emitted immediately
                    gate.receive()      // suspend until gate is released
                    emit(utt2)          // utt2 emitted only after gate opens
                }
        }

        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(
            utterances = emptyList(),  // not used; we provide a custom source
            scope = stdScope,
            contentSource = lazySource,
        )

        ctrl.start("book1", null)
        advanceUntilIdle()  // streaming job starts; utt1 appended; suspends at gate

        ctrl.play()
        advanceUntilIdle()  // play loop picks up utt1 and speaks it; then waits for stream

        // utt1 must have been spoken BEFORE we open the gate (utt2 not yet emitted)
        assertEquals("utt1 should be spoken before utt2 is emitted", 1, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)

        // Now release the gate so utt2 is emitted
        gate.send(Unit)
        advanceUntilIdle()  // utt2 appended; play loop wakes and speaks utt2

        assertEquals("both utterances should now be spoken", 2, provider.calls.size)
        assertEquals("Hello", provider.calls[1].text)
    }

    /**
     * Regression test for the stream-completion hang.
     *
     * The bug: the producer's stream-done signal was a no-op (a same-value
     * MutableStateFlow assignment, which is dropped) and `streamDone` was a plain
     * `var`, so the play loop's wait predicate was never re-evaluated when the
     * stream ended without producing a further utterance. If the play loop was
     * ALREADY suspended on the wait when the stream completed, the no-op failed
     * to wake it and the loop hung forever.
     *
     * This test reproduces that exact ordering with a gated source:
     *   1. emit(utt1)            — play loop speaks it, advances cursor to 1,
     *                              then suspends on the (loadedCount/streamDone) wait.
     *   2. gate.receive()        — stream is held here while the loop is WAITING.
     *   3. gate.send(Unit)       — stream resumes and immediately COMPLETES with no
     *                              further emit → streamDone flips false→true.
     * With the fix, the false→true streamDone emission wakes the combined wait and
     * the loop terminates. runTest fails on a stuck coroutine, so the old (buggy)
     * code re-hangs and fails this test.
     */
    @Test
    fun `play loop wakes and terminates when stream completes while loop is waiting - no hang`() = runTest {
        val gate = Channel<Unit>(Channel.RENDEZVOUS)

        val gatedSingleSource = object : TtsContentSource {
            override fun stream(bookId: String, fromLocator: Locator?, skipToSelection: Boolean): Flow<SentenceUtterance> =
                flow {
                    emit(utt1)        // play loop consumes this, then starts waiting
                    gate.receive()    // hold the stream OPEN while the loop is suspended
                    // completes here with NO further emit — streamDone false→true
                }
        }

        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(
            utterances = emptyList(),
            scope = stdScope,
            contentSource = gatedSingleSource,
        )

        ctrl.start("book1", null)
        advanceUntilIdle()  // bookId set; stream emits utt1 then blocks at gate.receive()
        ctrl.play()
        advanceUntilIdle()  // play loop speaks utt1, advances cursor to 1, then SUSPENDS on the wait

        // utt1 spoken; loop is now waiting (stream not yet done, cursor == size)
        assertEquals(1, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)
        assertEquals(PlaybackState.Playing("book1", loc1), ctrl.state.value)

        // Complete the stream while the loop is suspended — this must wake it.
        gate.send(Unit)
        advanceUntilIdle()  // must RETURN (not hang); loop wakes, sees streamDone, terminates

        // Still exactly one spoken; the loop reached the end and exited to Idle.
        assertEquals(1, provider.calls.size)
        assertEquals(PlaybackState.Idle, ctrl.state.value)
    }

    // ── C1 regression test: startAndPlay atomicity ────────────────────────────

    /**
     * C1 regression: [TtsPlaybackController.startAndPlay] must speak segments even when
     * called on a [StandardTestDispatcher]-backed scope where scope.launch is queued
     * (not eager). The play loop runs INSIDE the same launch block as setup, so
     * [currentBookId] is guaranteed to be set before the speak loop runs.
     *
     * This is the exact failure mode of the old start()+play() pattern:
     *   - start() queues setup in scope.launch → currentBookId set LATER
     *   - play() reads currentBookId synchronously BEFORE the queue drains → null → no-op
     *
     * With startAndPlay, both run in one launch so the race cannot occur.
     */
    @Test
    fun `startAndPlay speaks segments atomically - no start+play race`() = runTest {
        // Use StandardTestDispatcher so scope.launch is QUEUED (not eager).
        // This reproduces the race: with the old start()+play(), play() sees currentBookId==null.
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2), scope = stdScope)

        // Call startAndPlay — nothing runs yet (dispatcher is paused)
        ctrl.startAndPlay("book1", null)

        // Drain: startAndPlay's single launch block runs to completion
        advanceUntilIdle()

        // Both segments must have been spoken — proving the race is fixed
        assertEquals("startAndPlay must speak all segments without a race", 2, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)
        assertEquals("Hello", provider.calls[1].text)
        assertEquals(PlaybackState.Idle, ctrl.state.value)
    }

    /**
     * Proves the OLD separate start()+play() pattern WOULD fail on StandardTestDispatcher:
     * after start() is queued but before advanceUntilIdle(), play() sees currentBookId==null
     * and returns as a no-op, so no speech occurs. Documents the fixed race condition.
     */
    @Test
    fun `old start then play with no advanceUntilIdle would be a no-op proving the race`() = runTest {
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2), scope = stdScope)

        // Reproduce the race: start() queues setup; play() runs BEFORE the queue drains
        ctrl.start("book1", null)
        // Do NOT call advanceUntilIdle() between start and play — that's the race condition
        ctrl.play()  // currentBookId is still null → play() is a no-op

        // No speech has occurred yet (the race condition)
        assertEquals("play() is a no-op due to race — no speech yet", 0, provider.calls.size)
        // (If we now advance, the streamJob runs but no playJob was ever launched)
        advanceUntilIdle()
        assertEquals("still no speech — playJob was never launched", 0, provider.calls.size)
    }

    /**
     * BUG 2 + BUG 3 regression: a session started via [startAndPlay] must be
     * stoppable by [pause].
     *
     * Old bug: [startAndPlay] ran the speak loop in the OUTER scope.launch and never
     * assigned it to [playJob], so [pause]→[cancelPlay] (which cancels [playJob]) did
     * nothing — the loop kept running and overwrote Paused back to Playing.
     *
     * Setup: a GATED [FakeTtsProvider] parks the loop mid-speak on utt1. After one
     * speak we pause(); releasing the gate + advanceUntilIdle() must show NO further
     * speak calls and state == Paused (not Playing).
     *
     * BUG 3: pausing cancels the suspended provider.speak() → CancellationException.
     * That must propagate (not be swallowed) and emit NO providerError. We collect
     * providerError with Turbine and assert it stays empty.
     */
    @Test
    fun `startAndPlay then pause stops the loop - state Paused, no extra speak, no providerError`() = runTest {
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2, utt3), scope = stdScope)

        val errors = mutableListOf<String>()
        val errorCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ctrl.providerError.collect { errors += it }
        }

        // Arm gate so the FIRST speak() suspends, parking the loop mid-utterance.
        provider.armSpeakGate()

        ctrl.startAndPlay("book1", null)
        advanceUntilIdle()  // setup runs; loop speaks utt1's segment then parks at the gate

        // Exactly one speak happened and the loop is parked.
        assertEquals("loop should be parked after the first speak", 1, provider.calls.size)
        assertEquals("สวัสดี", provider.calls[0].text)
        assertTrue("state should be Playing while parked", ctrl.state.value is PlaybackState.Playing)

        // Pause — must cancel the (now tracked) playJob even though it started via startAndPlay.
        ctrl.pause()
        provider.releaseSpeakGate()  // unblock the cancelled speak so it can unwind
        advanceUntilIdle()

        // No further speak calls — the loop really stopped.
        assertEquals("pause must stop the loop — no further speak calls", 1, provider.calls.size)

        // State must be Paused (NOT overwritten back to Playing).
        val st = ctrl.state.value
        assertTrue("Expected Paused after pause, got $st", st is PlaybackState.Paused)

        // BUG 3: cancellation must NOT surface as a providerError.
        assertTrue("cancellation must not emit a providerError, got $errors", errors.isEmpty())

        errorCollector.cancel()
    }

    // ── ttsLocator persistence test ───────────────────────────────────────────

    // ── backpressure / OOM-bound test ─────────────────────────────────────────

    /**
     * Proves that the prefetch window is bounded to [TtsPlaybackController.AHEAD_BUFFER].
     *
     * A fake content source lazily emits 5_000 utterances. A GATED [FakeTtsProvider]
     * parks the speak loop at cursor 0 so the producer cannot be unblocked by
     * playback. After startAndPlay + advanceUntilIdle the producer must have
     * suspended after emitting at most AHEAD_BUFFER + 1 utterances — NOT 5_000.
     *
     * WITHOUT the fix the producer runs to completion (5_000 loaded) before the
     * play loop even starts; with the fix it halts at ~AHEAD_BUFFER and waits
     * for the cursor to advance.
     *
     * The second phase releases the gate and drains all 5_000 utterances to
     * verify: (a) no deadlock — the producer keeps refilling as the cursor
     * advances, and (b) correctness — all sentences are spoken and state
     * returns to Idle.
     */
    @Test
    fun `backpressure bounds prefetch to AHEAD_BUFFER ahead of cursor - OOM fix`() = runTest {
        val aheadBuffer = 64  // mirror TtsPlaybackController.AHEAD_BUFFER
        val totalUtterances = 5_000

        // Large lazy source — emits totalUtterances utterances without materialising them all.
        val largeSource = object : TtsContentSource {
            override fun stream(bookId: String, fromLocator: Locator?, skipToSelection: Boolean): Flow<SentenceUtterance> =
                flow {
                    repeat(totalUtterances) { i ->
                        emit(SentenceUtterance(
                            locator = locator("ch${i / 100}.xhtml", "Sentence $i"),
                            segments = listOf(SpeechSegment(Lang.EN, "sentence $i")),
                        ))
                    }
                }
        }

        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(
            utterances = emptyList(),
            scope = stdScope,
            contentSource = largeSource,
        )

        // Gate the speak loop at cursor 0 — producer cannot be unlocked by playback.
        provider.armSpeakGate()

        ctrl.startAndPlay("book1", null)
        advanceUntilIdle()

        // PHASE 1: producer must be suspended after ≤ AHEAD_BUFFER + 1 utterances loaded
        // (the +1 is the utterance that triggered the suspension check).
        val loadedAfterStart = ctrl.loadedCountForTest
        assertTrue(
            "Producer should be bounded to AHEAD_BUFFER + 1 (${ aheadBuffer + 1 }) but loaded $loadedAfterStart",
            loadedAfterStart <= aheadBuffer + 1,
        )
        // Without the fix this would be 5_000 (full run-away prefetch → OOM).
        assertTrue(
            "Producer should have loaded at least 1 utterance",
            loadedAfterStart >= 1,
        )

        // PHASE 2: release gate → play loop advances cursor → producer refills → all consumed.
        provider.releaseSpeakGate()
        advanceUntilIdle()

        assertEquals(
            "All $totalUtterances utterances must be spoken (no deadlock)",
            totalUtterances,
            provider.calls.size,
        )
        assertEquals(
            "State must be Idle after consuming all utterances",
            PlaybackState.Idle,
            ctrl.state.value,
        )
    }

    /**
     * Verifies that after playing a sentence the ttsLocator is persisted,
     * and that a subsequent start() with fromLocator=null resolves the
     * saved ttsLocator.
     */
    @Test
    fun `ttsLocator is saved after sentence advance and restored on next start`() = runTest {
        val stdScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val ctrl = controller(listOf(utt1, utt2), scope = stdScope)

        ctrl.start("book1", null)
        advanceUntilIdle()
        ctrl.play()
        advanceUntilIdle()

        // Advance past the 1s debounce so the ttsLocator save is flushed
        advanceTimeBy(1_100)
        runCurrent()

        // After playing, ttsLocator should be saved (pointing to last played sentence)
        val saved = progressRepo.getStored("book1")
        assertNotNull("ttsLocator should be persisted after playback", saved)
        assertNotNull("ttsLocator JSON should not be null", saved?.ttsLocator)
        // locator (reading progress) must NOT be overwritten by TTS progress
        assertNull("reading locator should remain null (TTS must not touch it)", saved?.locator)
    }
}
