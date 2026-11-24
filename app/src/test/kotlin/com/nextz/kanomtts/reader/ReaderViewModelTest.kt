package com.nextz.kanomtts.reader

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.ReadingSessionDao
import com.nextz.kanomtts.data.db.ReadingSessionEntity
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import com.nextz.kanomtts.reader.util.FakeBookmarkStore
import com.nextz.kanomtts.reader.util.FakeReaderBookSource
import com.nextz.kanomtts.reader.util.FakeReaderPreferences
import com.nextz.kanomtts.reader.util.FakeTtsPlaybackController
import com.nextz.kanomtts.reader.util.fakeReplacementRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val testScheduler = kotlinx.coroutines.test.TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(testScheduler)
    private lateinit var source: FakeReaderBookSource

    private val locatorA = Locator(
        href = Url("a.xhtml")!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(totalProgression = 0.3),
    )
    private val locatorB = Locator(
        href = Url("b.xhtml")!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(totalProgression = 0.5),
    )

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        source = FakeReaderBookSource()
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(bookId: String, ttsController: FakeTtsPlaybackController = FakeTtsPlaybackController()) =
        ReaderViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to bookId)),
            context = ApplicationProvider.getApplicationContext(),
            source = source,
            prefs = FakeReaderPreferences(),
            bookmarkStore = FakeBookmarkStore(),
            replacementRuleRepository = fakeReplacementRuleRepository(),
            ttsController = ttsController,
            readingSessionDao = object : ReadingSessionDao {
                override suspend fun insert(session: ReadingSessionEntity) = Unit
                override suspend fun insertAll(sessions: List<ReadingSessionEntity>) = Unit
                override suspend fun getAllForBackup(): List<ReadingSessionEntity> = emptyList()
                override suspend fun deleteAll() = Unit
                override suspend fun deleteForBook(bookId: String) = Unit
                override suspend fun deleteBefore(cutoff: Long) = Unit
                override fun observeAll(): Flow<List<ReadingSessionEntity>> = flowOf(emptyList())
                override fun observeTotalSince(fromEpochMs: Long): Flow<Long?> = flowOf(0L)
            },
        )

    @Test fun `volume keys use expected page direction with reverse off and on`() {
        val up = android.view.KeyEvent.KEYCODE_VOLUME_UP
        val down = android.view.KeyEvent.KEYCODE_VOLUME_DOWN
        assertEquals(true, volumeKeyMovesForward(up, reversed = false))
        assertEquals(false, volumeKeyMovesForward(down, reversed = false))
        assertEquals(false, volumeKeyMovesForward(up, reversed = true))
        assertEquals(true, volumeKeyMovesForward(down, reversed = true))
    }

    @Test fun `initial hardware key down turns exactly once`() {
        assertEquals(
            HardwareKeyDisposition.PAGE_TURN,
            hardwareKeyDisposition(android.view.KeyEvent.ACTION_DOWN, repeatCount = 0, isLongPress = false),
        )
    }

    @Test fun `held hardware key repeats are consumed without another page turn`() {
        // The disposition is independent of direction, so this protects both Next and Previous.
        listOf(1, 2, 3, 10).forEach { repeat ->
            assertEquals(
                HardwareKeyDisposition.CONSUME,
                hardwareKeyDisposition(android.view.KeyEvent.ACTION_DOWN, repeatCount = repeat, isLongPress = false),
            )
        }
        assertEquals(
            HardwareKeyDisposition.CONSUME,
            hardwareKeyDisposition(android.view.KeyEvent.ACTION_DOWN, repeatCount = 0, isLongPress = true),
        )
    }

    @Test fun `hardware key up is left for device release handling`() {
        assertEquals(
            HardwareKeyDisposition.IGNORE,
            hardwareKeyDisposition(android.view.KeyEvent.ACTION_UP, repeatCount = 0, isLongPress = false),
        )
    }

    @Test fun `missing book yields Error state`() = runTest {
        source.book = null
        val vm = vm("missing")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value is ReaderUiState.Error)
    }

    @Test fun `failed publication open yields Error state`() = runTest {
        source.book = BookEntity("1", "content://x", "T", null, null, null, 1L, 1L, null)
        source.publication = null // open fails
        val vm = vm("1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertTrue(vm.uiState.value is ReaderUiState.Error)
    }

    @Test fun `rapid locator changes persist once with the latest`() = runTest(dispatcher) {
        val vm = vm("1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        // Let the VM's init coroutines (load + debounce pipeline) start up.
        runCurrent()

        vm.onLocatorChanged(locatorA)
        vm.onLocatorChanged(locatorB)

        // Advance past the 800 ms debounce window and drain pending coroutines.
        advanceTimeBy(900)
        runCurrent()

        // ReaderViewModel now uses saveReadingLocator (partial-column update, I2),
        // not saveProgress, to avoid clobbering the ttsLocator column.
        assertEquals(1, source.readingLocatorSaveCount)
        assertEquals(LocatorSerializer.encode(locatorB), source.lastReadingLocator)
    }

    @Test fun `reader chrome auto hide timer resets after interaction and fires after three seconds`() = runTest(dispatcher) {
        var visible = true
        val timer = ReaderChromeAutoHideTimer(this) { visible = false }

        timer.restart()
        advanceTimeBy(2_000)
        runCurrent()
        timer.restart() // Any reader interaction restarts the timeout.
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(visible)

        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(false, visible)
        timer.cancel()
    }

    // ─── toggleTts branching tests ────────────────────────────────────────────
    // Navigator is not bound in unit tests, so the idle path calls startAndPlay(bookId, null)
    // (firstVisibleElementLocator and currentLocator are both unavailable without a bound
    // EpubNavigatorFragment). The playing/paused branches are fully testable without a navigator.

    @Test fun `toggleTts when idle calls startAndPlay (not separate start+play)`() = runTest(dispatcher) {
        val fake = FakeTtsPlaybackController()
        val vm = vm("42", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()

        vm.toggleTts()
        runCurrent()

        // C1 regression: idle path must use startAndPlay — not separate start()+play()
        assertEquals(1, fake.startAndPlayCalls.size)
        assertEquals("42", fake.startAndPlayCalls[0].bookId)
        assertEquals(0, fake.startCalls.size)  // start() alone must NOT be called
        assertEquals(0, fake.playCalls)         // play() alone must NOT be called
        assertEquals(0, fake.pauseCalls)
    }

    @Test fun `toggleTts when playing calls pause only`() = runTest(dispatcher) {
        val fake = FakeTtsPlaybackController()
        fake.emitState(PlaybackState.Playing("42", locatorA))
        val vm = vm("42", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()

        vm.toggleTts()
        runCurrent()

        assertEquals(0, fake.startCalls.size)
        assertEquals(0, fake.playCalls)
        assertEquals(1, fake.pauseCalls)
    }

    @Test fun `toggleTts when paused calls play only`() = runTest(dispatcher) {
        val fake = FakeTtsPlaybackController()
        fake.emitState(PlaybackState.Paused("42", locatorA))
        val vm = vm("42", fake)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()

        vm.toggleTts()
        runCurrent()

        assertEquals(0, fake.startCalls.size)
        assertEquals(1, fake.playCalls)
        assertEquals(0, fake.pauseCalls)
    }
}
