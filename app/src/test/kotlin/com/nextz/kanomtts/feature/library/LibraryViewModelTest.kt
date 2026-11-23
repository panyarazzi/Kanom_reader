package com.nextz.kanomtts.feature.library

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.repository.BookDeleter
import com.nextz.kanomtts.feature.library.util.FakeBookRepository
import com.nextz.kanomtts.feature.library.util.FakeProgressRepository
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.playback.PlaybackStopper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeBooks: FakeBookRepository
    private lateinit var fakeProgress: FakeProgressRepository
    private lateinit var vm: LibraryViewModel

    private class FakeBookDeleter : BookDeleter {
        val deleted = mutableSetOf<String>()
        override suspend fun delete(bookId: String) { deleted += bookId }
    }

    private class FakeStopper(initial: PlaybackState) : PlaybackStopper {
        override val state: StateFlow<PlaybackState> = MutableStateFlow(initial)
        var stopCount = 0
        override fun stop() { stopCount++ }
    }

    private fun locator() = Locator(href = Url("c.xhtml")!!, mediaType = MediaType.XHTML, text = Locator.Text())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeBooks = FakeBookRepository()
        fakeProgress = FakeProgressRepository()
        vm = LibraryViewModel(fakeBooks, fakeProgress, FakeBookDeleter(), FakeStopper(PlaybackState.Idle))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun book(
        id: String,
        title: String,
        uri: String = "content://test/$id",
        lastOpenedAt: Long? = null,
    ) = BookEntity(
        id = id,
        uri = uri,
        title = title,
        author = "Author $id",
        lang = null,
        coverPath = null,
        fileSize = 100L,
        addedAt = 1000L,
        lastOpenedAt = lastOpenedAt,
    )

    private fun progress(bookId: String, percent: Float, updatedAt: Long = 0L) = ProgressEntity(
        bookId = bookId,
        locator = null,
        ttsLocator = null,
        percent = percent,
        updatedAt = updatedAt,
    )

    @Test
    fun `empty repo emits empty books`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        // fakeBooks starts empty
        val state = vm.uiState.value
        assertTrue(state.books.isEmpty())
        assertNull(state.continueCard)
    }

    @Test
    fun `two books map to two BookCards`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Alpha"), book("2", "Beta"))
        val state = vm.uiState.value
        assertEquals(2, state.books.size)
        assertEquals("Alpha", state.books.find { it.id == "1" }!!.title)
        assertEquals("Beta", state.books.find { it.id == "2" }!!.title)
    }

    @Test
    fun `progress 0_5 gives percent=0_5 and isReading=true`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Alpha"))
        fakeProgress.setProgress("1", progress("1", 0.5f))
        val card = vm.uiState.value.books.first()
        assertEquals(0.5f, card.percent)
        assertTrue(card.isReading)
    }

    @Test
    fun `progress 0 gives isReading=false`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Alpha"))
        fakeProgress.setProgress("1", progress("1", 0f))
        val card = vm.uiState.value.books.first()
        assertEquals(0f, card.percent)
        assertFalse(card.isReading)
    }

    @Test
    fun `progress 1 gives isReading=false`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Alpha"))
        fakeProgress.setProgress("1", progress("1", 1f))
        val card = vm.uiState.value.books.first()
        assertEquals(1f, card.percent)
        assertFalse(card.isReading)
    }

    @Test
    fun `setQuery filters by title case-insensitively`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Alpha"), book("2", "Beta"))
        vm.setQuery("alph")
        val state = vm.uiState.value
        assertEquals(1, state.books.size)
        assertEquals("Alpha", state.books.first().title)
    }

    @Test
    fun `search results include matching books inside folder containers`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("1", "Alpha").copy(sourceFolder = "Books/Fiction"),
            book("2", "Beta").copy(sourceFolder = "Books/History"),
        )
        runCurrent()
        assertTrue(vm.uiState.value.books.isEmpty())
        vm.setQuery("Alpha")
        runCurrent()
        assertEquals(listOf("1"), vm.uiState.value.books.map { it.id })
    }

    @Test
    fun `setQuery empty shows all books`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Alpha"), book("2", "Beta"))
        vm.setQuery("alph")
        vm.setQuery("")
        assertEquals(2, vm.uiState.value.books.size)
    }

    @Test
    fun `continueCard is the most recently opened in-progress book`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("1", "OldInProgress", lastOpenedAt = 1000L),
            book("2", "NewInProgress", lastOpenedAt = 2000L),
            book("3", "Finished", lastOpenedAt = 3000L),
        )
        fakeProgress.setProgress("1", progress("1", 0.3f))
        fakeProgress.setProgress("2", progress("2", 0.6f))
        fakeProgress.setProgress("3", progress("3", 1.0f))
        val state = vm.uiState.value
        assertNotNull(state.continueCard)
        assertEquals("2", state.continueCard!!.id)
    }

    @Test
    fun `continueCards contains at most four most recently opened in-progress books`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = (1..6).map { book("$it", "Book $it", lastOpenedAt = it.toLong()) }
        (1..6).forEach { fakeProgress.setProgress("$it", progress("$it", 0.2f)) }
        runCurrent()
        assertEquals(listOf("6", "5", "4", "3"), vm.uiState.value.continueCards.map { it.id })
        assertEquals("6", vm.uiState.value.continueCard?.id)
    }

    @Test
    fun `continueCard is null when no in-progress books`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("1", "Finished", lastOpenedAt = 1000L))
        fakeProgress.setProgress("1", progress("1", 1.0f))
        assertNull(vm.uiState.value.continueCard)
    }

    @Test
    fun `setViewMode updates viewMode in state`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(LibraryViewMode.GRID, vm.uiState.value.viewMode)
        vm.setViewMode(LibraryViewMode.LIST)
        assertEquals(LibraryViewMode.LIST, vm.uiState.value.viewMode)
    }

    @Test
    fun setQuery_filters_by_uri_filename() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("1", "Title One", uri = "content://provider/MyBook.epub"),
            book("2", "Title Two", uri = "content://provider/OtherBook.epub"),
        )
        vm.setQuery("mybook")
        assertEquals(1, vm.uiState.value.books.size)
        assertEquals("1", vm.uiState.value.books.first().id)
    }


    @Test
    fun `folder cards group imported books and folder filter narrows library`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("1", "Alpha").copy(sourceFolder = "Series/One"),
            book("2", "Beta").copy(sourceFolder = "Series/One"),
            book("3", "Gamma").copy(sourceFolder = "Series/Two"),
        )
        runCurrent()
        assertEquals(1, vm.uiState.value.folders.size)
        assertEquals("Series", vm.uiState.value.folders.first().name)
        assertEquals(3, vm.uiState.value.folders.first().count)
        assertTrue(vm.uiState.value.books.isEmpty())
        vm.setFolderFilter("Series")
        runCurrent()
        assertEquals(2, vm.uiState.value.folders.size)
        val oneFolder = vm.uiState.value.folders.first { it.name == "One" }
        vm.setFolderFilter(oneFolder.path)
        runCurrent()
        assertEquals(listOf("1", "2"), vm.uiState.value.books.map { it.id })
    }

    @Test
    fun `shelf counts and filters follow reading progress and folder grouping`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("reading", "Reading").copy(sourceFolder = "Series/One"),
            book("unread", "Unread"),
            book("finished", "Finished"),
        )
        fakeProgress.setProgress("reading", progress("reading", 0.4f))
        fakeProgress.setProgress("finished", progress("finished", 1f))
        runCurrent()

        assertEquals(1, vm.uiState.value.readingCount)
        assertEquals(1, vm.uiState.value.toReadCount)
        assertEquals(1, vm.uiState.value.seriesCount)
        assertEquals(1, vm.uiState.value.finishedCount)

        vm.setShelfFilter(LibraryShelf.READING)
        runCurrent()
        assertEquals(listOf("reading"), vm.uiState.value.books.map { it.id })

        vm.setShelfFilter(LibraryShelf.READING)
        runCurrent()
        assertEquals(3, vm.uiState.value.books.size)
    }

    @Test
    fun `completion threshold is consistent across reading and finished shelves`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("reading", "Reading"),
            book("finished", "Finished"),
            book("unread", "Unread"),
        )
        fakeProgress.setProgress("reading", progress("reading", 0.998f))
        fakeProgress.setProgress("finished", progress("finished", 0.999f))
        fakeProgress.setProgress("unread", progress("unread", 0f))
        runCurrent()

        assertEquals(1, vm.uiState.value.readingCount)
        assertEquals(1, vm.uiState.value.finishedCount)
        assertTrue(vm.uiState.value.books.first { it.id == "finished" }.isReading.not())

        vm.setShelfFilter(LibraryShelf.FINISHED)
        runCurrent()
        assertEquals(listOf("finished"), vm.uiState.value.books.map { it.id })
    }

    @Test
    fun `series shelf count represents books grouped into folders`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("one", "One").copy(sourceFolder = "Collection/Set A"),
            book("two", "Two").copy(sourceFolder = "Collection/Set A"),
            book("three", "Three"),
        )
        runCurrent()
        assertEquals(2, vm.uiState.value.seriesCount)
        vm.setShelfFilter(LibraryShelf.SERIES)
        runCurrent()
        assertEquals(listOf("one", "two"), vm.uiState.value.books.map { it.id }.sorted())
    }

    @Test
    fun `recently opened and newest book rows use their respective timestamps`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("old", "Old", lastOpenedAt = 100L).copy(addedAt = 1000L),
            book("recent", "Recent", lastOpenedAt = 300L).copy(addedAt = 2000L),
            book("middle", "Middle", lastOpenedAt = 200L).copy(addedAt = 4000L),
        )
        runCurrent()

        assertEquals(listOf("recent", "middle", "old"), vm.uiState.value.recentlyOpened.map { it.id })
        assertEquals(listOf("middle", "recent", "old"), vm.uiState.value.newestBooks.map { it.id })
    }

    @Test
    fun `filename sorting and source filename display can be selected`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("1", "Zulu").copy(sourceFileName = "a.epub"),
            book("2", "Alpha").copy(sourceFileName = "z.epub"),
        )
        runCurrent()
        vm.setSortMode(LibrarySortMode.FILENAME_ASC)
        vm.setShowFilename(true)
        runCurrent()
        assertEquals(listOf("1", "2"), vm.uiState.value.books.map { it.id })
        assertEquals("a.epub", vm.uiState.value.books.first().displayTitle)
    }

    @Test
    fun `secondary sort orders equal titles by source filename`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(
            book("z", "Same").copy(sourceFileName = "z-file.epub"),
            book("a", "Same").copy(sourceFileName = "a-file.epub"),
        )
        runCurrent()
        vm.setSortMode(LibrarySortMode.TITLE_ASC)
        vm.setSecondarySortMode(LibrarySecondarySortMode.FILENAME_ASC)
        runCurrent()
        assertEquals(listOf("a", "z"), vm.uiState.value.books.map { it.id })
        vm.setSecondarySortMode(LibrarySecondarySortMode.FILENAME_DESC)
        runCurrent()
        assertEquals(listOf("z", "a"), vm.uiState.value.books.map { it.id })
    }

    // ── Task 3: selection + delete ────────────────────────────────────────────

    @Test
    fun `toggleSelection adds then removes`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("b1", "Book 1"))
        runCurrent()
        vm.toggleSelection("b1"); runCurrent()
        assertEquals(setOf("b1"), vm.uiState.value.selectedIds)
        vm.toggleSelection("b1"); runCurrent()
        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
    }

    @Test
    fun `deleteSelected deletes each, stops a playing selected book, clears selection`() = runTest {
        val deleter = FakeBookDeleter()
        val stopper = FakeStopper(PlaybackState.Playing("b1", locator()))
        val localVm = LibraryViewModel(fakeBooks, fakeProgress, deleter, stopper)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { localVm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("b1", "Book 1"), book("b2", "Book 2"))
        runCurrent()
        localVm.toggleSelection("b1"); localVm.toggleSelection("b2"); runCurrent()
        localVm.deleteSelected(); runCurrent()
        assertEquals(setOf("b1", "b2"), deleter.deleted)
        assertEquals(1, stopper.stopCount)
        assertEquals(emptySet<String>(), localVm.uiState.value.selectedIds)
    }

    @Test
    fun `deleteSelected does not stop playback when no selected book is playing`() = runTest {
        val stopper = FakeStopper(PlaybackState.Playing("other", locator()))
        val localVm = LibraryViewModel(fakeBooks, fakeProgress, FakeBookDeleter(), stopper)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { localVm.uiState.collect {} }
        fakeBooks.books.value = listOf(book("b1", "Book 1"))
        runCurrent()
        localVm.toggleSelection("b1"); runCurrent()
        localVm.deleteSelected(); runCurrent()
        assertEquals(0, stopper.stopCount)
    }
}
