package com.nextz.kanomtts.playback

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM + virtual-time TDD for [SleepTimer].
 *
 * Strategy: all tests use [runTest] whose [testScheduler] is shared with the
 * [SleepTimer]'s [CoroutineScope], so [advanceTimeBy] drives the timer's
 * [delay] calls deterministically.
 *
 * - [runCurrent] pumps any immediately-ready tasks (e.g. the launch of the
 *   countdown coroutine) without advancing virtual time.
 * - [advanceTimeBy] moves the clock forward by a precise amount, running
 *   exactly those ticks that fall within that window.
 *
 * No Robolectric needed — [SleepTimer] has no Android dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    // ── countdown fires at the correct time ──────────────────────────────────

    @Test
    fun `60s countdown fires expiration exactly once after 60 seconds`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        val expirationCount = mutableListOf<Unit>()
        val collectJob = launch { timer.expirations.collect { expirationCount.add(it) } }

        timer.start(60_000L, false)
        runCurrent()  // launch the countdown coroutine

        // Before 60s: no expiration
        advanceTimeBy(59_000L)
        assertEquals("no expiration before 60s", 0, expirationCount.size)

        // Complete the countdown
        advanceTimeBy(2_000L)
        assertEquals("exactly one expiration at ~60s", 1, expirationCount.size)

        // No second expiration after further waiting
        advanceTimeBy(60_000L)
        assertEquals("no additional expirations after expiry", 1, expirationCount.size)

        collectJob.cancel()
    }

    // ── remaining counts down ────────────────────────────────────────────────

    @Test
    fun `remaining starts at full duration and ticks down to 0`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        timer.start(60_000L, false)
        runCurrent()  // launch countdown; sets remaining to 60_000 initially

        assertEquals("remaining should start at 60_000", 60_000L, timer.remaining.value)

        advanceTimeBy(30_000L)
        val mid = timer.remaining.value ?: -1L
        assert(mid in 29_000L..31_000L) { "remaining mid-countdown should be ~30s, got $mid" }

        advanceTimeBy(31_000L)
        assertEquals("remaining should reach 0 at expiry", 0L, timer.remaining.value)
    }

    // ── fadeFraction ramp ────────────────────────────────────────────────────

    @Test
    fun `fadeFraction is 1f before fade window then ramps to 0f at expiry`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        timer.start(60_000L, false)
        runCurrent()

        // Well before fade window (> 5s remaining) — should be 1f
        advanceTimeBy(50_000L)
        assertEquals("fadeFraction should be 1f before fade window", 1f, timer.fadeFraction.value)

        // At 57s elapsed → 3s remaining inside the fade window → fraction = 3000/5000 = 0.6f
        advanceTimeBy(7_000L)
        val fracIn = timer.fadeFraction.value
        assert(fracIn in 0.4f..0.8f) {
            "fadeFraction at ~57s elapsed should be in fade ramp, got $fracIn"
        }
        assert(fracIn < 1f) { "fadeFraction should be < 1f in fade window, got $fracIn" }

        // At expiry → 0f
        advanceTimeBy(5_000L)
        assertEquals("fadeFraction should be 0f at expiry", 0f, timer.fadeFraction.value)
    }

    @Test
    fun `fadeFraction mid-fade is strictly between 0 and 1`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        // 10s timer, FADE_MS=5s. At 7s elapsed → 3s remaining → 3000/5000 = 0.6f
        // advanceTimeBy(N) runs ticks at t=1000..N-1 (exclusive upper bound), so
        // advanceTimeBy(7_001) runs ticks at 1s..7s → remaining=3000ms.
        timer.start(10_000L, false)
        runCurrent()

        advanceTimeBy(7_001L)
        val frac = timer.fadeFraction.value
        assert(frac > 0f && frac < 1f) {
            "fadeFraction should be strictly between 0 and 1 in the fade window, got $frac"
        }
        // Specifically: 3000ms remaining → 3000/5000 = 0.6
        assert(frac in 0.5f..0.7f) {
            "fadeFraction with ~3s remaining in 5s fade window should be ~0.6, got $frac"
        }
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    fun `cancel mid-countdown prevents expiration and resets state`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        val expirationCount = mutableListOf<Unit>()
        val collectJob = launch { timer.expirations.collect { expirationCount.add(it) } }

        timer.start(60_000L, false)
        runCurrent()

        advanceTimeBy(30_000L)
        assertEquals("no expiration at mid-point", 0, expirationCount.size)

        // Cancel mid-countdown
        timer.cancel()
        runCurrent()

        assertNull("remaining should be null after cancel", timer.remaining.value)
        assertEquals("fadeFraction should be 1f after cancel", 1f, timer.fadeFraction.value)

        // Advance past where expiry would have been — no expiration
        advanceTimeBy(60_000L)
        assertEquals("no expiration should fire after cancel", 0, expirationCount.size)

        collectJob.cancel()
    }

    // ── end-of-chapter mode ──────────────────────────────────────────────────

    @Test
    fun `end-of-chapter mode does not fire on time advance but fires on boundary`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        val expirationCount = mutableListOf<Unit>()
        val collectJob = launch { timer.expirations.collect { expirationCount.add(it) } }

        timer.start(null, true)
        runCurrent()

        // Advance a long time — should NOT expire on its own
        advanceTimeBy(3_600_000L)  // 1 hour
        assertEquals("end-of-chapter should NOT expire on time alone", 0, expirationCount.size)
        assertNull("remaining should be null in EOC mode", timer.remaining.value)
        assertEquals("fadeFraction should be 1f in EOC mode", 1f, timer.fadeFraction.value)

        // Trigger boundary — should fire exactly once
        timer.onChapterBoundary()
        runCurrent()
        assertEquals("expiration should fire on chapter boundary", 1, expirationCount.size)

        // Second boundary call — should NOT fire again (already disarmed)
        timer.onChapterBoundary()
        runCurrent()
        assertEquals("second boundary call should not fire again", 1, expirationCount.size)

        collectJob.cancel()
    }

    @Test
    fun `onChapterBoundary without arming does nothing`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        val expirationCount = mutableListOf<Unit>()
        val collectJob = launch { timer.expirations.collect { expirationCount.add(it) } }

        // Call without ever arming
        timer.onChapterBoundary()
        runCurrent()
        assertEquals("boundary without arming should not fire", 0, expirationCount.size)

        collectJob.cancel()
    }

    // ── restart cancels prior timer ──────────────────────────────────────────

    @Test
    fun `start cancels prior countdown and restarts fresh`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        val expirationCount = mutableListOf<Unit>()
        val collectJob = launch { timer.expirations.collect { expirationCount.add(it) } }

        // Start a 60s timer, advance 30s, then restart with a new 10s timer
        timer.start(60_000L, false)
        runCurrent()

        advanceTimeBy(30_000L)
        assertEquals("no expiration at mid-point of first timer", 0, expirationCount.size)

        // Restart with 10s — cancels the 60s timer
        timer.start(10_000L, false)
        runCurrent()
        assertEquals("remaining should reset to 10_000", 10_000L, timer.remaining.value)

        // Advance past original 60s: only the 10s timer should have fired once
        advanceTimeBy(60_000L)
        assertEquals("only one expiration from the restarted 10s timer", 1, expirationCount.size)

        collectJob.cancel()
    }

    // ── turbine smoke-test for expirations flow ──────────────────────────────

    @Test
    fun `expirations flow emits Unit via turbine on countdown completion`() = runTest {
        val timerScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val timer = SleepTimer(timerScope)

        timer.expirations.test {
            timer.start(5_000L, false)
            runCurrent()
            advanceTimeBy(6_000L)
            awaitItem()  // asserts exactly one Unit was emitted
            cancelAndIgnoreRemainingEvents()
        }
    }
}
