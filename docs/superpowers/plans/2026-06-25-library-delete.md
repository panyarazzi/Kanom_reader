# Library Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Long-press multi-select + bulk delete in the library, with a correct cleanup cascade (no Room FK cascade — every dependent handled explicitly) and a TTS-stop for a deleted playing book.

**Architecture:** A testable `BookDeleter` seam runs the per-book cascade (release SAF permission → delete cover file → delete orphan Progress/Bookmark/book-scoped-rule rows → delete the book row). `LibraryViewModel` gains selection state + `deleteSelected()` (which also stops TTS via a small `PlaybackStopper` seam if a deleted book is playing). The library UI gets a selection mode: long-press → checkbox overlays + a selection bar + a confirm dialog.

**Tech Stack:** Kotlin, Room 2.8.4 (queries only — no migration), Hilt, Coroutines, Compose/Material3 (`combinedClickable`, `Checkbox`, `AlertDialog`, `BackHandler`), JUnit4 + Robolectric 4.16.1.

## Global Constraints

- **Toolchain:** AGP 9.2.1 BUILT-IN Kotlin 2.2.10 (no Kotlin plugins on `:app`); Java 17; Room 2.8.4 via KSP. **No Room migration** — this plan only adds `@Query` methods (schema unchanged).
- **Cascade (no FK cascade exists):** for a deleted book, explicitly release the SAF persistable URI permission, delete the cover file, delete its Progress + Bookmark rows + its **book-scoped** replacement rules (`scope='book' AND bookId`), and delete the book row. **Global replacement rules are never touched.**
- **External side effects are `runCatching`-wrapped** (SAF release, cover-file delete) so one failure can't abort the cascade or crash; the DB deletes are awaited.
- **The EPUB file is NOT deleted** — it's a SAF `content://` URI the app never copied. Delete removes the library entry + releases the permission only.
- **No undo** — a confirm dialog ("ลบ N เล่ม?") is the only safeguard; delete is then immediate.
- **Spoken-path / TTS:** if a deleted book is the currently-playing/paused one (`PlaybackState.Playing/Paused.bookId`), stop playback (clears the mini-player). This decision lives in the ViewModel, not `BookDeleter`.
- **Defaults / no behaviour change when not selecting:** `selectedIds` defaults empty; `isSelectionMode` is `selectedIds.isNotEmpty()`; tapping a book opens it exactly as today unless in selection mode.
- **Tests:** `app/src/test/kotlin/...`, JUnit4 + `org.junit.Assert.*`, backtick names. Room/Android tests extend `RoomTestBase` (in-memory Room, `allowMainThreadQueries()`, `@Config(sdk=[35])`, `RobolectricTestRunner`). VM tests add `Dispatchers.setMain(UnconfinedTestDispatcher())`/`resetMain()`.
- **Per-task verification MUST include `:app:lintDebug`** (the 09a lesson). Commands: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`, `./gradlew :app:lintDebug`.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/nextz/kanomtts/data/repository/BookDeleter.kt` — the seam interface (Task 2).
- `app/src/main/kotlin/com/nextz/kanomtts/data/repository/DefaultBookDeleter.kt` — the cascade impl (Task 2).
- `app/src/main/kotlin/com/nextz/kanomtts/playback/PlaybackStopper.kt` — narrow `state`+`stop()` seam (Task 3).
- `app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackBindings.kt` — binds `PlaybackStopper` → `TtsPlaybackController` (Task 3).
- Tests: `DeleteByBookDaoTest.kt` (Task 1), `DefaultBookDeleterTest.kt` (Task 2), `LibraryViewModelTest.kt` (Task 3).

**Modify:**
- `data/db/ProgressDao.kt`, `BookmarkDao.kt`, `ReplacementRuleDao.kt` (+ `deleteByBook`) — Task 1.
- `data/di/RepositoryBindings.kt` (+ `bookDeleter` binding) — Task 2.
- `playback/TtsPlaybackController.kt` (implements `PlaybackStopper`) — Task 3.
- `feature/library/LibraryUiState.kt` (+ `selectedIds`, `isSelectionMode`) — Task 3.
- `feature/library/LibraryViewModel.kt` (selection state + actions) — Task 3.
- `feature/library/LibraryScreen.kt`, `LibraryComponents.kt` (selection UI) — Task 4.

---

## Task 1: `deleteByBook` DAO queries (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/ProgressDao.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/BookmarkDao.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDao.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/db/DeleteByBookDaoTest.kt`

**Interfaces:**
- Produces: `ProgressDao.deleteByBook(bookId)`, `BookmarkDao.deleteByBook(bookId)`, `ReplacementRuleDao.deleteByBook(bookId)` — each `suspend fun … (bookId: String)`.

- [ ] **Step 1: Write the failing test**

`DeleteByBookDaoTest.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteByBookDaoTest : RoomTestBase() {
    @Test fun `progress deleteByBook removes only that book`() = runTest {
        val dao = db.progressDao()
        dao.upsert(ProgressEntity(bookId = "b1", locator = null, ttsLocator = null, percent = 0.5f, updatedAt = 0L))
        dao.upsert(ProgressEntity(bookId = "b2", locator = null, ttsLocator = null, percent = 0.2f, updatedAt = 0L))
        dao.deleteByBook("b1")
        assertNull(dao.get("b1"))
        assertNotNull(dao.get("b2"))
    }

    @Test fun `bookmark deleteByBook removes only that book`() = runTest {
        val dao = db.bookmarkDao()
        dao.insert(BookmarkEntity(bookId = "b1", locator = "{}", snippet = "s1", createdAt = 0L))
        dao.insert(BookmarkEntity(bookId = "b2", locator = "{}", snippet = "s2", createdAt = 0L))
        dao.deleteByBook("b1")
        assertEquals(0, dao.observeForBook("b1").first().size)
        assertEquals(1, dao.observeForBook("b2").first().size)
    }

    @Test fun `replacement deleteByBook removes that book's rules but keeps global`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(ReplacementRuleEntity(scope = "book", bookId = "b1", pattern = "a", replacement = "b", isRegex = false, enabled = true, position = 0))
        dao.insert(ReplacementRuleEntity(scope = "book", bookId = "b2", pattern = "c", replacement = "d", isRegex = false, enabled = true, position = 1))
        dao.insert(ReplacementRuleEntity(scope = "global", bookId = null, pattern = "g", replacement = "h", isRegex = false, enabled = true, position = 2))
        dao.deleteByBook("b1")
        assertEquals(emptyList<String>(), dao.observeByBook("b1").first().map { it.pattern })
        assertEquals(listOf("c"), dao.observeByBook("b2").first().map { it.pattern })
        assertEquals(listOf("g"), dao.observeGlobal().first().map { it.pattern })
    }
}
```

- [ ] **Step 2: Run — verify fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*DeleteByBookDaoTest"`
Expected: FAIL — `deleteByBook` unresolved.

- [ ] **Step 3: Implement**

`ProgressDao.kt` — add (after `updateReadingLocator`):
```kotlin
    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
```
`BookmarkDao.kt` — add (after `delete(id: Long)`):
```kotlin
    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
```
`ReplacementRuleDao.kt` — add (after `delete(id: Long)`):
```kotlin
    @Query("DELETE FROM replacement_rules WHERE scope = 'book' AND bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
```

- [ ] **Step 4: Run — verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*DeleteByBookDaoTest"` → PASS.

- [ ] **Step 5: Build + lint + commit**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/db/ProgressDao.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/db/BookmarkDao.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDao.kt \
        app/src/test/kotlin/com/nextz/kanomtts/data/db/DeleteByBookDaoTest.kt
git commit -m "feat(db): deleteByBook queries for progress, bookmarks, book-scoped rules"
```

---

## Task 2: `BookDeleter` seam + cascade (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/data/repository/BookDeleter.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/data/repository/DefaultBookDeleter.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/di/RepositoryBindings.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/repository/DefaultBookDeleterTest.kt`

**Interfaces:**
- Consumes: `BookDao.getById(id): BookEntity?`, `BookDao.delete(id)`, the three `deleteByBook` from Task 1, `BookEntity(id, uri, …, coverPath)`.
- Produces: `interface BookDeleter { suspend fun delete(bookId: String) }`; `DefaultBookDeleter` (Hilt-bound).

- [ ] **Step 1: Write the failing test**

`DefaultBookDeleterTest.kt`:
```kotlin
package com.nextz.kanomtts.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DefaultBookDeleterTest : RoomTestBase() {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun deleter() = DefaultBookDeleter(
        db.bookDao(), db.progressDao(), db.bookmarkDao(), db.replacementRuleDao(), ctx,
    )

    private suspend fun book(id: String, uri: String, cover: String?) = db.bookDao().upsert(
        BookEntity(id = id, uri = uri, title = id, author = null, lang = null,
            coverPath = cover, fileSize = 1L, addedAt = 0L, lastOpenedAt = null),
    )

    @Test fun `delete removes the book, its rows, cover file, releases SAF; keeps global rule and other book`() = runTest {
        val coverFile = File.createTempFile("cover", ".png", ctx.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val uri = "content://test/b1.epub"
        ctx.contentResolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)

        book("b1", uri, coverFile.absolutePath)
        book("b2", "content://test/b2.epub", null)
        db.progressDao().upsert(ProgressEntity("b1", null, null, 0.5f, 0L))
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b1", locator = "{}", snippet = "s", createdAt = 0L))
        db.replacementRuleDao().insert(ReplacementRuleEntity(scope = "book", bookId = "b1", pattern = "a", replacement = "b", isRegex = false, enabled = true, position = 0))
        db.replacementRuleDao().insert(ReplacementRuleEntity(scope = "global", bookId = null, pattern = "g", replacement = "h", isRegex = false, enabled = true, position = 1))

        deleter().delete("b1")

        assertNull(db.bookDao().getById("b1"))
        assertNotNull(db.bookDao().getById("b2"))
        assertNull(db.progressDao().get("b1"))
        assertEquals(0, db.bookmarkDao().observeForBook("b1").first().size)
        assertEquals(emptyList<String>(), db.replacementRuleDao().observeByBook("b1").first().map { it.pattern })
        assertEquals(listOf("g"), db.replacementRuleDao().observeGlobal().first().map { it.pattern })
        assertFalse(coverFile.exists())
        assertEquals(0, ctx.contentResolver.persistedUriPermissions.size)
    }

    @Test fun `delete is a no-op when the book is already gone`() = runTest {
        deleter().delete("missing") // must not throw
        assertEquals(0, db.bookmarkDao().observeForBook("missing").first().size)
    }
}
```
> Robolectric 4.16.1's `ShadowContentResolver` supports `takePersistableUriPermission` / `persistedUriPermissions` / `releasePersistableUriPermission`. If `persistedUriPermissions` is unavailable in the shadow at runtime, keep the take/release calls (prod is `runCatching`-wrapped) and drop only that one assertion — report it if so.

- [ ] **Step 2: Run — verify fail.** `./gradlew :app:testDebugUnitTest --tests "*DefaultBookDeleterTest"` → FAIL (`BookDeleter`/`DefaultBookDeleter` unresolved).

- [ ] **Step 3: Implement the seam + cascade**

`BookDeleter.kt`:
```kotlin
package com.nextz.kanomtts.data.repository

/** Deletes a book and all of its dependents (no Room FK cascade exists). */
interface BookDeleter {
    suspend fun delete(bookId: String)
}
```
`DefaultBookDeleter.kt`:
```kotlin
package com.nextz.kanomtts.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookmarkDao
import com.nextz.kanomtts.data.db.ProgressDao
import com.nextz.kanomtts.data.db.ReplacementRuleDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DefaultBookDeleter @Inject constructor(
    private val bookDao: BookDao,
    private val progressDao: ProgressDao,
    private val bookmarkDao: BookmarkDao,
    private val replacementRuleDao: ReplacementRuleDao,
    @ApplicationContext private val context: Context,
) : BookDeleter {
    override suspend fun delete(bookId: String) {
        val book = bookDao.getById(bookId) ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(book.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        book.coverPath?.let { runCatching { File(it).delete() } }
        progressDao.deleteByBook(bookId)
        bookmarkDao.deleteByBook(bookId)
        replacementRuleDao.deleteByBook(bookId)
        bookDao.delete(bookId)
    }
}
```

- [ ] **Step 4: Bind it** — add to `RepositoryBindings.kt` (imports + one `@Binds`):
```kotlin
import com.nextz.kanomtts.data.repository.BookDeleter
import com.nextz.kanomtts.data.repository.DefaultBookDeleter
```
```kotlin
    @Binds @Singleton abstract fun bookDeleter(impl: DefaultBookDeleter): BookDeleter
```

- [ ] **Step 5: Run — verify pass.** `./gradlew :app:testDebugUnitTest --tests "*DefaultBookDeleterTest"` → PASS.

- [ ] **Step 6: Build + lint + commit**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/repository/BookDeleter.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/repository/DefaultBookDeleter.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/di/RepositoryBindings.kt \
        app/src/test/kotlin/com/nextz/kanomtts/data/repository/DefaultBookDeleterTest.kt
git commit -m "feat(library): BookDeleter cascade — SAF release, cover file, orphan rows, book row"
```

---

## Task 3: `PlaybackStopper` seam + `LibraryViewModel` selection + delete (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/PlaybackStopper.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackBindings.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/TtsPlaybackController.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryUiState.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryViewModel.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/library/LibraryViewModelTest.kt`

**Interfaces:**
- Consumes: `BookDeleter` (Task 2); `PlaybackState.Playing/Paused(bookId, locator)`; `TtsPlaybackController` (has `override val state: StateFlow<PlaybackState>` + `fun stop()`); `BookRepository(dao)` / `ProgressRepository(dao)` over the in-memory db.
- Produces: `interface PlaybackStopper { val state: StateFlow<PlaybackState>; fun stop() }`; `LibraryUiState.selectedIds`/`isSelectionMode`; `LibraryViewModel.toggleSelection`/`clearSelection`/`deleteSelected`; top-level `playingBookIsSelected(state, selected): Boolean`.

- [ ] **Step 1: Write the failing test**

`LibraryViewModelTest.kt`:
```kotlin
package com.nextz.kanomtts.feature.library

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.repository.BookDeleter
import com.nextz.kanomtts.data.repository.BookRepository
import com.nextz.kanomtts.data.repository.ProgressRepository
import com.nextz.kanomtts.data.util.RoomTestBase
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
class LibraryViewModelTest : RoomTestBase() {
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

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

    private suspend fun seed(id: String) = db.bookDao().upsert(
        BookEntity(id = id, uri = "content://$id", title = id, author = null, lang = null,
            coverPath = null, fileSize = 1L, addedAt = 0L, lastOpenedAt = null),
    )
    private fun vm(deleter: BookDeleter, stopper: PlaybackStopper) = LibraryViewModel(
        bookReader = BookRepository(db.bookDao()),
        progressReader = ProgressRepository(db.progressDao()),
        bookDeleter = deleter,
        playbackStopper = stopper,
    )

    @Test fun `toggleSelection adds then removes`() = runTest {
        seed("b1")
        val vm = vm(FakeBookDeleter(), FakeStopper(PlaybackState.Idle))
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.toggleSelection("b1"); runCurrent()
        assertEquals(setOf("b1"), vm.uiState.value.selectedIds)
        vm.toggleSelection("b1"); runCurrent()
        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
    }

    @Test fun `deleteSelected deletes each, stops a playing selected book, clears selection`() = runTest {
        seed("b1"); seed("b2")
        val deleter = FakeBookDeleter()
        val stopper = FakeStopper(PlaybackState.Playing("b1", locator()))
        val vm = vm(deleter, stopper)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.toggleSelection("b1"); vm.toggleSelection("b2"); runCurrent()
        vm.deleteSelected(); runCurrent()
        assertEquals(setOf("b1", "b2"), deleter.deleted)
        assertEquals(1, stopper.stopCount)
        assertEquals(emptySet<String>(), vm.uiState.value.selectedIds)
    }

    @Test fun `deleteSelected does not stop playback when no selected book is playing`() = runTest {
        seed("b1")
        val stopper = FakeStopper(PlaybackState.Playing("other", locator()))
        val vm = vm(FakeBookDeleter(), stopper)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        vm.toggleSelection("b1"); runCurrent()
        vm.deleteSelected(); runCurrent()
        assertEquals(0, stopper.stopCount)
    }
}
```
> Confirm `BookRepository`/`ProgressRepository` constructor params (`(dao: BookDao)` / `(dao: ProgressDao)`); adapt the `vm()` helper to the real signatures if they differ.

- [ ] **Step 2: Run — verify fail.** `./gradlew :app:testDebugUnitTest --tests "*LibraryViewModelTest"` → FAIL.

- [ ] **Step 3: Add `PlaybackStopper` + bind it**

`PlaybackStopper.kt`:
```kotlin
package com.nextz.kanomtts.playback

import kotlinx.coroutines.flow.StateFlow

/** Narrow seam for components that need to observe + stop playback (e.g. delete a playing book). */
interface PlaybackStopper {
    val state: StateFlow<PlaybackState>
    fun stop()
}
```
`TtsPlaybackController.kt` — add the interface to the class header and mark `stop()` override:
```kotlin
class TtsPlaybackController @Inject constructor(
    // ... unchanged ...
) : TtsPlaybackSource, VoicePreviewer, PlaybackStopper {
```
```kotlin
    override fun stop() {
        scope.launch { tearDown() }
    }
```
(The existing `override val state: StateFlow<PlaybackState>` already satisfies `PlaybackStopper.state`.)

`playback/di/PlaybackBindings.kt`:
```kotlin
package com.nextz.kanomtts.playback.di

import com.nextz.kanomtts.playback.PlaybackStopper
import com.nextz.kanomtts.playback.TtsPlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackBindings {
    @Binds @Singleton abstract fun playbackStopper(impl: TtsPlaybackController): PlaybackStopper
}
```
> If a playback Hilt `@Module abstract class … { @Binds … }` already exists, add the binding there instead of creating this file (adapt; report which you did).

- [ ] **Step 4: Add selection state to `LibraryUiState`**
```kotlin
data class LibraryUiState(
    val books: List<BookCard> = emptyList(),
    val continueCard: BookCard? = null,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val query: String = "",
    val selectedIds: Set<String> = emptySet(),
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}
```

- [ ] **Step 5: Wire `LibraryViewModel`**

Add the predicate (top-level in the file, after the class or above it):
```kotlin
fun playingBookIsSelected(state: PlaybackState, selected: Set<String>): Boolean = when (state) {
    is PlaybackState.Playing -> state.bookId in selected
    is PlaybackState.Paused -> state.bookId in selected
    else -> false
}
```
Constructor + state + combine + actions:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    bookReader: BookReader,
    progressReader: ProgressReader,
    private val bookDeleter: BookDeleter,
    private val playbackStopper: PlaybackStopper,
) : ViewModel() {

    private val _viewMode = MutableStateFlow(LibraryViewMode.GRID)
    private val _query = MutableStateFlow("")
    private val _selectedIds = MutableStateFlow(emptySet<String>())

    // ... booksWithProgress unchanged ...

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithProgress,
        _viewMode,
        _query,
        _selectedIds,
    ) { pairs, viewMode, query, selectedIds ->
        // ... bookById / allCards / filteredCards / continueCard unchanged ...
        LibraryUiState(
            books = filteredCards,
            continueCard = continueCard,
            viewMode = viewMode,
            query = query,
            selectedIds = selectedIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(),
    )

    fun setViewMode(mode: LibraryViewMode) { _viewMode.value = mode }
    fun setQuery(query: String) { _query.value = query }

    fun toggleSelection(bookId: String) {
        _selectedIds.value = _selectedIds.value.let { if (bookId in it) it - bookId else it + bookId }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (playingBookIsSelected(playbackStopper.state.value, ids)) playbackStopper.stop()
            ids.forEach { bookDeleter.delete(it) }
            clearSelection()
        }
    }
    // ... toBookCard / uriFilename unchanged ...
}
```
Add imports: `com.nextz.kanomtts.data.repository.BookDeleter`, `com.nextz.kanomtts.playback.PlaybackState`, `com.nextz.kanomtts.playback.PlaybackStopper`, `kotlinx.coroutines.launch`.

- [ ] **Step 6: Run — verify pass.** `./gradlew :app:testDebugUnitTest --tests "*LibraryViewModelTest"` → PASS.

- [ ] **Step 7: Build + full suite + lint + commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/PlaybackStopper.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackBindings.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/TtsPlaybackController.kt \
        app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryUiState.kt \
        app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryViewModel.kt \
        app/src/test/kotlin/com/nextz/kanomtts/feature/library/LibraryViewModelTest.kt
git commit -m "feat(library): selection state + deleteSelected (stops TTS for a deleted playing book)"
```

---

## Task 4: Selection-mode UI

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryComponents.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryScreen.kt`

**Interfaces:**
- Consumes: `LibraryUiState.selectedIds`/`isSelectionMode`; `LibraryViewModel.toggleSelection`/`clearSelection`/`deleteSelected`.

No new unit test (Compose plumbing; device-validated). Verify by build + lint + full suite.

- [ ] **Step 1: `LibraryComponents.kt` — imports**

Add:
```kotlin
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
```
(Remove the now-unused `androidx.compose.foundation.clickable` import only if no other composable in the file still uses `.clickable` — `ContinueHero`/`ViewModeToggle` DO use it, so keep the import.)

- [ ] **Step 2: `LibraryComponents.kt` — `SelectionBar` (add a new composable, e.g. after `LibrarySearchBar`)**
```kotlin
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "ปิด")
        }
        Text(
            text = "เลือก $count เล่ม",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "ลบ")
        }
    }
}
```

- [ ] **Step 3: `LibraryComponents.kt` — `BookGridItem` (selection-aware)**

Replace the whole `BookGridItem` with:
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(
    card: BookCard,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() else onClick() },
                onLongClick = onToggle,
            )
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            CoverImage(
                coverPath = card.coverPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.medium),
            )
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.percent > 0f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { card.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }
}
```

- [ ] **Step 4: `LibraryComponents.kt` — `BookListItem` (selection-aware)**

Replace the whole `BookListItem` with:
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    card: BookCard,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() else onClick() },
                onLongClick = onToggle,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
        CoverImage(
            coverPath = card.coverPath,
            modifier = Modifier
                .size(64.dp, 90.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!card.author.isNullOrBlank()) {
                Text(
                    text = card.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.percent > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { card.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
                Text(
                    text = "${(card.percent * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 5: `LibraryScreen.kt` — `LibraryContent` (selection bar, item wiring, confirm dialog)**

Add imports:
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```
Change the `LibraryContent` signature to add the three callbacks:
```kotlin
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onViewMode: (LibraryViewMode) -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    var showConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        if (state.isSelectionMode) {
            SelectionBar(
                count = state.selectedIds.size,
                onClose = onClearSelection,
                onDelete = { showConfirm = true },
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySearchBar(query = state.query, onQueryChange = onQueryChange, modifier = Modifier.weight(1f))
                ViewModeToggle(mode = state.viewMode, onSelect = onViewMode)
            }
        }

        if (state.continueCard != null && !state.isSelectionMode) {
            Spacer(Modifier.height(12.dp))
            ContinueHero(card = state.continueCard, onClick = { onOpenBook(state.continueCard.id) })
        }

        Spacer(Modifier.height(12.dp))

        if (state.books.isEmpty() && state.query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = "ยังไม่มีหนังสือ — แตะ + เพื่อเพิ่ม",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            when (state.viewMode) {
                LibraryViewMode.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.books, key = { it.id }) { card ->
                            BookGridItem(
                                card = card,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = card.id in state.selectedIds,
                                onClick = { onOpenBook(card.id) },
                                onToggle = { onToggleSelection(card.id) },
                            )
                        }
                    }
                }
                LibraryViewMode.LIST, LibraryViewMode.DETAIL -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.books, key = { it.id }) { card ->
                            BookListItem(
                                card = card,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = card.id in state.selectedIds,
                                onClick = { onOpenBook(card.id) },
                                onToggle = { onToggleSelection(card.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = { onDeleteSelected(); showConfirm = false }) { Text("ลบ") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("ยกเลิก") }
            },
            title = { Text("ลบ ${state.selectedIds.size} เล่ม?") },
            text = { Text("การลบนี้ย้อนกลับไม่ได้") },
        )
    }
}
```
Update the three `@Preview` calls to pass the new callbacks (`onToggleSelection = {}, onClearSelection = {}, onDeleteSelected = {}`).

- [ ] **Step 6: `LibraryScreen.kt` — `LibraryScreen` (FAB hide, BackHandler, wire callbacks)**

Add import: `import androidx.activity.compose.BackHandler`. In the `LibraryScreen` body, after `val uiState by …`:
```kotlin
    BackHandler(enabled = uiState.isSelectionMode) { viewModel.clearSelection() }
```
Hide the FAB in selection mode:
```kotlin
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                FloatingActionButton(
                    onClick = { launcher.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                    modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "เพิ่มหนังสือ")
                }
            }
        },
```
Pass the callbacks to `LibraryContent`:
```kotlin
        LibraryContent(
            state = uiState,
            onQueryChange = viewModel::setQuery,
            onViewMode = viewModel::setViewMode,
            onOpenBook = onOpenBook,
            onToggleSelection = viewModel::toggleSelection,
            onClearSelection = viewModel::clearSelection,
            onDeleteSelected = viewModel::deleteSelected,
            contentPadding = combinedPadding,
            modifier = modifier,
        )
```

- [ ] **Step 7: Build + full suite + lint**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; green; lint clean.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryComponents.kt \
        app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryScreen.kt
git commit -m "feat(library): selection-mode UI — long-press, selection bar, checkboxes, confirm delete"
```

---

## Final integration pass (after Task 4)

- [ ] Full gate: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` (run `:app:testDebugUnitTest --rerun-tasks` once) — all green.
- [ ] Opus whole-branch review; record Minors in `.superpowers/sdd/progress.md`.
- [ ] **Device matrix:** long-press a book → selection mode (checkboxes + bar); select several; trash → confirm → books vanish from the grid; the cover thumbnails are gone (cache cleared / no broken images); deleting the **currently-playing** book stops TTS + hides the mini-player; the continue-hero updates; system back exits selection mode; tapping a book outside selection mode still opens it.
- [ ] `finishing-a-development-branch` (device sign-off gate, per the established cadence).

---

## Self-Review (completed during authoring)

**Spec coverage:** cascade (Task 2) ↔ §3.1; `deleteByBook` queries (Task 1) ↔ §3.2; selection state + TTS-stop (Task 3) ↔ §3.3; selection UI + confirm + BackHandler + hide-FAB (Task 4) ↔ §3.4; edge cases (continue-hero recompute, missing-book no-op) ↔ §3.5; testing strategy ↔ §6.
**No migration:** confirmed — only `@Query` additions.
**Type consistency:** `BookDeleter.delete(bookId)`, `deleteByBook(bookId)`, `PlaybackStopper(state, stop)`, `playingBookIsSelected(state, selected)`, `LibraryUiState.selectedIds`/`isSelectionMode`, `toggleSelection`/`clearSelection`/`deleteSelected`, the `BookGridItem`/`BookListItem` `(isSelectionMode, isSelected, onClick, onToggle)` signatures are used consistently across producing/consuming tasks.
**Deviation from spec (deliberate):** §3.3 implied the VM injects `TtsPlaybackController` directly; the plan introduces a narrow `PlaybackStopper` seam (Task 3) so the VM is unit-testable with a fake — the controller still backs it (same `@Singleton`). Same behaviour, better testability.
**Implementer must confirm on first open:** `BookRepository`/`ProgressRepository` constructor params (Task 3 test); whether a playback Hilt `@Binds` module already exists (Task 3 Step 3); that Robolectric 4.16.1's shadow exposes `persistedUriPermissions` (Task 2 test note).
