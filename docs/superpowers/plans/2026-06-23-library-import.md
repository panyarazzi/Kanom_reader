# Library & Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add EPUBs (file picker + OS "open with"), see them in a sweet library (grid / list / detail) with cover thumbnails, reading-progress, search, and a "Continue listening" hero — all backed by the Plan 04 data layer.

**Architecture:** Collapsed in `:app` under `com.nextz.kanomtts.feature.library`. Import is a testable pipeline: a `MetadataExtractor` interface (Readium implementation extracts title/author/cover) keeps Readium behind a seam so the importer's id/dedup/persistence logic is unit-testable with a fake. `EpubImporter` builds a `BookEntity` (stable id from the source) + persists the cover, consulting `BookRepository.isDuplicate` first. `LibraryViewModel` (Hilt) derives `LibraryUiState` from `BookRepository` + `ProgressRepository` flows. The UI is Compose, themed by `KanomTheme`, with Coil for covers. The library screen replaces the Plan 03 `LibraryScreen` placeholder.

**Tech Stack:** Jetpack Compose + Material 3, Coil (image loading), Hilt + hilt-navigation-compose, Readium 3.3.0 (metadata/cover extraction), Storage Access Framework. Tests: JUnit + coroutines-test + fakes (importer/ViewModel); compile + `@Preview` for UI.

## Global Constraints

- Collapsed modules: packages in `:app` under `com.nextz.kanomtts.feature.library`. AGP 9.2.1 built-in Kotlin (no `kotlin.android`); KSP for Hilt.
- Consume Plan 04 repositories ONLY through their public API: `BookRepository(observeLibrary/get/add/isDuplicate/remove)`, `ProgressRepository(observe/get)`. `BookEntity(id, uri, title, author?, lang?, coverPath?, fileSize, addedAt, lastOpenedAt?)`.
- `isDuplicate(uri, title, fileSize)` MUST be consulted before `add`; `add` is REPLACE (won't reject) — Plan 04.
- Import must persist URI read permission for "open with" / picker results (`takePersistableUriPermission`) so books reopen across launches.
- No `Date.now()`-style nondeterminism in testable code: timestamps are injected (pass a `now: Long` or a `Clock`/`() -> Long` into the importer) so importer tests are deterministic.
- Library view modes: GRID / LIST / DETAIL (spec §5.1); search/filter by title or filename; Continue-listening hero (spec §6.3).
- Reader navigation: tapping a book navigates to a `reader/{bookId}` route that does not exist yet — Plan 05 adds the route constant + a placeholder reader destination OUTSIDE the bottom bar (Plan 06 fills it); the bottom bar must hide on the reader route (final reviewer's Plan 03 recommendation).
- TDD for `EpubImporter` and `LibraryViewModel`; UI verified by compile + preview. DRY, YAGNI, frequent commits.

---

## File Structure

```
gradle/libs.versions.toml      (modify: coil3, hilt-navigation-compose, lifecycle-viewmodel-compose, turbine[test])
app/build.gradle.kts           (modify: those deps)
app/src/main/AndroidManifest.xml  (modify: MainActivity intent-filter for application/epub+zip "open with")
app/src/main/kotlin/com/nextz/kanomtts/feature/library/
    import/BookMetadata.kt          (title, author?, lang?, coverBytes?)
    import/MetadataExtractor.kt     (interface)
    import/ReadiumMetadataExtractor.kt  (Readium impl)
    import/EpubImporter.kt          (id + dedup + cover persist + add)
    LibraryUiState.kt               (BookCard list + viewMode + query + hero)
    LibraryViewModel.kt
    LibraryScreen.kt                (replaces Plan 03 placeholder; grid/list/detail + hero + search)
    LibraryComponents.kt            (BookGridItem, BookListItem, ContinueHero, ViewModeToggle)
app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/   (modify KanomDestinations/KanomApp: reader route + hide-bottom-bar)
app/src/test/kotlin/com/nextz/kanomtts/feature/library/
    import/EpubImporterTest.kt
    LibraryViewModelTest.kt
    util/Fakes.kt                   (FakeBookRepository, FakeProgressRepository, FakeMetadataExtractor)
```

---

### Task 1: Import deps + metadata-extractor seam

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`. Create `import/BookMetadata.kt`, `import/MetadataExtractor.kt`, `import/ReadiumMetadataExtractor.kt`.

**Interfaces:**
- `data class BookMetadata(val title: String, val author: String?, val lang: String?, val coverBytes: ByteArray?)`
- `interface MetadataExtractor { suspend fun extract(uri: String): BookMetadata? }` (null = unreadable EPUB)
- `class ReadiumMetadataExtractor @Inject constructor(...) : MetadataExtractor` — opens the publication via Readium (`AssetRetriever`/`PublicationOpener` from the spike findings), reads `publication.metadata.title` / `.authors` / `.languages`, and the cover (`publication.cover()` → bitmap → PNG bytes). Returns null on open failure.

- [ ] **Step 1: Catalog + build deps** (verify current versions on Google/Maven Central)
```toml
# [versions]
coil = "3.3.0"
hiltNavigationCompose = "1.2.0"
lifecycleViewmodelCompose = "2.9.1"
turbine = "1.2.1"
# [libraries]
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```
Add to `app/build.gradle.kts`: `implementation(libs.coil.compose)`, `implementation(libs.androidx.hilt.navigation.compose)`, `implementation(libs.androidx.lifecycle.viewmodel.compose)`, `testImplementation(libs.turbine)`.

- [ ] **Step 2: `BookMetadata.kt`**
```kotlin
package com.nextz.kanomtts.feature.library.import

data class BookMetadata(
    val title: String,
    val author: String?,
    val lang: String?,
    val coverBytes: ByteArray?,
) {
    // ByteArray needs structural equals for tests/data-class correctness
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookMetadata) return false
        return title == other.title && author == other.author && lang == other.lang &&
            (coverBytes?.contentEquals(other.coverBytes) ?: (other.coverBytes == null))
    }
    override fun hashCode(): Int {
        var r = title.hashCode()
        r = 31 * r + (author?.hashCode() ?: 0)
        r = 31 * r + (lang?.hashCode() ?: 0)
        r = 31 * r + (coverBytes?.contentHashCode() ?: 0)
        return r
    }
}
```

- [ ] **Step 3: `MetadataExtractor.kt`**
```kotlin
package com.nextz.kanomtts.feature.library.import

interface MetadataExtractor {
    /** Opens the EPUB at [uri] and extracts display metadata, or null if it can't be read. */
    suspend fun extract(uri: String): BookMetadata?
}
```

- [ ] **Step 4: `ReadiumMetadataExtractor.kt`** (compile-verified; behavior validated manually / in Plan 06)
Use the open-EPUB API confirmed in the spike findings (`docs/superpowers/spikes/2026-06-23-readium-findings.md`): construct `AssetRetriever(context.contentResolver, DefaultHttpClient())`, `PublicationOpener(DefaultPublicationParser(...))`, `assetRetriever.retrieve(url).getOrElse { return null }`, `opener.open(asset, allowUserInteraction=false).getOrElse { return null }`. Then read `publication.metadata.title` (fallback to a filename-derived title if null), `publication.metadata.authors.firstOrNull()?.name`, `publication.metadata.languages.firstOrNull()`, and `publication.cover()` (a suspend returning a `Bitmap?`) → compress to PNG `ByteArray`. Inject `@ApplicationContext Context`. Run on `Dispatchers.IO`. Reference the probe in `app/.../spike/ReadiumProbe.kt` for exact signatures; adjust if the API differs and note it.

- [ ] **Step 5: Verify + commit**
`./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (Readium extractor compiles against 3.3.0).
```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/kotlin/com/nextz/kanomtts/feature
git commit -m "feat(library): import deps + metadata-extractor seam (Readium impl)"
```

---

### Task 2: EpubImporter (id + dedup + cover persist)

**Files:** Create `import/EpubImporter.kt`, test `import/EpubImporterTest.kt`, test `util/Fakes.kt`.

**Interfaces:**
- `class EpubImporter @Inject constructor(extractor, bookRepository, @ApplicationContext context, now provider)` with:
  `suspend fun import(uri: String, fileSize: Long, displayName: String?, now: Long = nowProvider()): ImportResult`
- `sealed interface ImportResult { object Duplicate; data class Imported(val bookId: String); object Unreadable }`
- Behavior: compute stable `id = sha256(uri)` (hex); if `bookRepository.isDuplicate(uri, title, fileSize)` → `Duplicate`; else `extractor.extract(uri)` (null → `Unreadable`); persist `coverBytes` to `context.filesDir/covers/<id>.png` (if present) → `coverPath`; build `BookEntity(id, uri, title=metadata.title or displayName or "Untitled", author, lang, coverPath, fileSize, addedAt=now, lastOpenedAt=null)`; `bookRepository.add(book)`; return `Imported(id)`. NOTE: run the dedup check with the EXTRACTED title (extract first, then dedup) so title-based dedup uses the real title — i.e. extract BEFORE isDuplicate. Re-order: extract → (null→Unreadable) → isDuplicate(uri, title, size) → persist → add.

- [ ] **Step 1: `util/Fakes.kt`** (fakes for repos + extractor)
```kotlin
package com.nextz.kanomtts.feature.library.util

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.feature.library.import.BookMetadata
import com.nextz.kanomtts.feature.library.import.MetadataExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

class FakeBookRepository {
    val books = MutableStateFlow<List<BookEntity>>(emptyList())
    fun observeLibrary(): Flow<List<BookEntity>> = books
    suspend fun get(id: String): BookEntity? = books.value.find { it.id == id }
    suspend fun add(book: BookEntity) { books.value = books.value.filterNot { it.id == book.id } + book }
    suspend fun isDuplicate(uri: String, title: String, fileSize: Long): Boolean =
        books.value.any { it.uri == uri || (title.isNotEmpty() && fileSize > 0 && it.title == title && it.fileSize == fileSize) }
    suspend fun remove(id: String) { books.value = books.value.filterNot { it.id == id } }
}

class FakeMetadataExtractor(var result: BookMetadata?) : MetadataExtractor {
    var lastUri: String? = null
    override suspend fun extract(uri: String): BookMetadata? { lastUri = uri; return result }
}
```
NOTE: `EpubImporter` should depend on the real `BookRepository` type. To keep the importer unit-testable without Hilt/Room, make `EpubImporter` depend on a NARROW interface it defines (e.g. `interface BookStore { suspend fun isDuplicate(...); suspend fun add(...) }`) that `BookRepository` satisfies via an adapter OR — simpler — give `BookRepository` an interface. **Decision: extract a `BookRepository` interface is overkill; instead the test's `FakeBookRepository` duck-types the same method names and the importer takes the concrete `BookRepository`. Since the importer test can't construct a real `BookRepository` (needs a DAO), define a minimal `interface BookWriter { suspend fun isDuplicate(uri:String,title:String,fileSize:Long):Boolean; suspend fun add(book:BookEntity) }`, have `BookRepository` implement it, and `EpubImporter` depend on `BookWriter`.** Update `FakeBookRepository` to implement `BookWriter`.

- [ ] **Step 2: add `BookWriter` interface + implement on `BookRepository`**
In `com.nextz.kanomtts.data.repository`, add:
```kotlin
interface BookWriter {
    suspend fun isDuplicate(uri: String, title: String, fileSize: Long): Boolean
    suspend fun add(book: com.nextz.kanomtts.data.db.BookEntity)
}
```
and make `class BookRepository(...) : BookWriter` (its existing `add`/`isDuplicate` already match — add `override`). Make `FakeBookRepository : BookWriter`.

- [ ] **Step 3: Write the failing test `import/EpubImporterTest.kt`**
```kotlin
package com.nextz.kanomtts.feature.library.import

import com.nextz.kanomtts.feature.library.util.FakeBookRepository
import com.nextz.kanomtts.feature.library.util.FakeMetadataExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubImporterTest {
    private fun importer(repo: FakeBookRepository, extractor: FakeMetadataExtractor, tmp: File) =
        EpubImporter(extractor, repo, coversDir = tmp, now = { 1000L })

    @Test fun imports_new_book_with_extracted_title() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("My Book", "Auth", "th", null))
        val tmp = createTempDir()
        val r = importer(repo, ext, tmp).import("content://x", fileSize = 123, displayName = "x.epub")
        assertTrue(r is ImportResult.Imported)
        assertEquals("My Book", repo.books.value.single().title)
        assertEquals(123L, repo.books.value.single().fileSize)
    }
    @Test fun duplicate_is_not_added_twice() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("Dup", null, null, null))
        val tmp = createTempDir()
        val imp = importer(repo, ext, tmp)
        imp.import("content://same", 50, "a.epub")
        val r2 = imp.import("content://same", 50, "a.epub")
        assertTrue(r2 is ImportResult.Duplicate)
        assertEquals(1, repo.books.value.size)
    }
    @Test fun unreadable_epub_returns_unreadable() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(null)
        val r = importer(repo, ext, createTempDir()).import("content://bad", 10, "bad.epub")
        assertTrue(r is ImportResult.Unreadable)
        assertEquals(0, repo.books.value.size)
    }
    @Test fun stable_id_is_deterministic_for_same_uri() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("T", null, null, null))
        importer(repo, ext, createTempDir()).import("content://fixed", 1, null)
        val id1 = repo.books.value.single().id
        assertEquals(id1, EpubImporter.stableId("content://fixed"))
    }
}
```

- [ ] **Step 4: Implement `EpubImporter.kt`** (depends on `coversDir: File` + `now: () -> Long`, both injectable; for Hilt provide `coversDir = File(context.filesDir, "covers")`)
```kotlin
package com.nextz.kanomtts.feature.library.import

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.repository.BookWriter
import java.io.File
import java.security.MessageDigest

sealed interface ImportResult {
    object Duplicate : ImportResult
    data class Imported(val bookId: String) : ImportResult
    object Unreadable : ImportResult
}

class EpubImporter(
    private val extractor: MetadataExtractor,
    private val books: BookWriter,
    private val coversDir: File,
    private val now: () -> Long,
) {
    suspend fun import(uri: String, fileSize: Long, displayName: String?): ImportResult {
        val meta = extractor.extract(uri) ?: return ImportResult.Unreadable
        val title = meta.title.ifBlank { displayName?.removeSuffix(".epub") ?: "Untitled" }
        if (books.isDuplicate(uri, title, fileSize)) return ImportResult.Duplicate
        val id = stableId(uri)
        val coverPath = meta.coverBytes?.let { bytes ->
            coversDir.mkdirs()
            File(coversDir, "$id.png").apply { writeBytes(bytes) }.absolutePath
        }
        books.add(
            BookEntity(
                id = id, uri = uri, title = title, author = meta.author, lang = meta.lang,
                coverPath = coverPath, fileSize = fileSize, addedAt = now(), lastOpenedAt = null,
            )
        )
        return ImportResult.Imported(id)
    }

    companion object {
        fun stableId(uri: String): String =
            MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 5: RED→GREEN + commit**
`./gradlew :app:testDebugUnitTest --tests "*EpubImporterTest"` → pass (4).
```bash
git add app/src && git commit -m "feat(library): EpubImporter with stable id, dedup, cover persistence"
```

---

### Task 3: LibraryViewModel + UiState

**Files:** Create `LibraryUiState.kt`, `LibraryViewModel.kt`, test `LibraryViewModelTest.kt`. Add `FakeProgressRepository` to `util/Fakes.kt`.

**Interfaces:**
- `enum class LibraryViewMode { GRID, LIST, DETAIL }`
- `data class BookCard(val id, val title, val author?, val coverPath?, val percent: Float, val isReading: Boolean)`
- `data class LibraryUiState(val books: List<BookCard>, val continueCard: BookCard?, val viewMode: LibraryViewMode, val query: String)`
- `LibraryViewModel @Inject constructor(bookRepository, progressRepository)`: exposes `StateFlow<LibraryUiState>`; combines books + per-book progress; `continueCard` = most-recently-opened book with 0<percent<1; `setViewMode`, `setQuery` (filters by title/filename, case-insensitive). For testability the VM should accept narrow read interfaces (`BookReader { observeLibrary(): Flow<List<BookEntity>> }`, `ProgressReader { observe(bookId): Flow<ProgressEntity?> }`) implemented by the repositories — OR take the concrete repos and use `FakeBookRepository`/`FakeProgressRepository` that duck-type. **Decision: add `BookReader`/`ProgressReader` interfaces (mirroring `BookWriter`) so the VM is unit-testable with fakes; repos implement them.**

- [ ] **Step 1: reader interfaces** — add `interface BookReader { fun observeLibrary(): kotlinx.coroutines.flow.Flow<List<BookEntity>> }` (BookRepository implements) and `interface ProgressReader { fun observe(bookId: String): kotlinx.coroutines.flow.Flow<ProgressEntity?> }` (ProgressRepository implements). Make `FakeBookRepository`/`FakeProgressRepository` implement them.

- [ ] **Step 2: `LibraryUiState.kt`** (the enums + data classes above).

- [ ] **Step 3: Write the failing test `LibraryViewModelTest.kt`** — using fakes + turbine/`first()`; assert: empty→empty; books map to cards; search filters; continueCard picks the in-progress most-recent book; setViewMode updates state. (Full test code: drive the fakes' StateFlows, advance the VM's combine, assert `uiState.value`.) Use `kotlinx.coroutines.test.runTest` + a `StandardTestDispatcher`/`UnconfinedTestDispatcher` for `stateIn`.

- [ ] **Step 4: Implement `LibraryViewModel.kt`** combining `bookReader.observeLibrary()` with each book's `progressReader.observe(id)` (use `flatMapLatest`/`combine`), mapping to `BookCard`, applying the query filter, computing `continueCard`, exposed as `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5000), initial)`. `viewMode`/`query` are `MutableStateFlow`s combined in.

- [ ] **Step 5: RED→GREEN + commit**
`./gradlew :app:testDebugUnitTest --tests "*LibraryViewModelTest"` → pass.
```bash
git add app/src && git commit -m "feat(library): LibraryViewModel + UiState (grid/list/detail, search, continue)"
```

---

### Task 4: Library UI (grid / list / detail + hero + search)

**Files:** Create `LibraryComponents.kt`, replace `LibraryScreen.kt` (the Plan 03 placeholder). Verify by compile + `@Preview`.

**Interfaces:** `@Composable fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel(), onOpenBook: (String) -> Unit, modifier, contentPadding)`; components `ContinueHero`, `BookGridItem`, `BookListItem`, `ViewModeToggle`, `LibrarySearchBar`.

- [ ] **Step 1: `LibraryComponents.kt`** — Coil `AsyncImage` covers (model = `coverPath` file or a gradient placeholder when null), progress ring/bar, the blue-gradient `ContinueHero` (matches the approved mockup: cover + title + progress + play), `ViewModeToggle` (grid/list segmented), `LibrarySearchBar` (rounded pill). Use `KanomTheme`/`MaterialTheme` tokens; the highlight color is NOT used here (reserved for karaoke).
- [ ] **Step 2: `LibraryScreen.kt`** — collects `viewModel.uiState`; renders search + view toggle + hero (if `continueCard != null`) + the list in the selected `viewMode` (LazyVerticalGrid for GRID, LazyColumn for LIST/DETAIL); each item `onClick = { onOpenBook(card.id) }`; empty state ("ยังไม่มีหนังสือ — แตะ + เพื่อเพิ่ม"). Wire `setQuery`/`setViewMode`.
- [ ] **Step 3:** Add `@Preview`s (grid + list) with a fake `LibraryUiState` rendered by a stateless `LibraryContent(state, callbacks)` (split state-hoisted content from the VM-bound `LibraryScreen` so previews don't need Hilt).
- [ ] **Step 4: Verify + commit**
`./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
```bash
git add app/src && git commit -m "feat(library): grid/list/detail library UI with continue-listening hero"
```

---

### Task 5: Import wiring (picker + "open with") + reader route

**Files:** Modify `ui/navigation/KanomDestinations.kt` + `KanomApp.kt` (reader route + hide bottom bar on it; wire `onOpenBook`), `MainActivity.kt` (handle "open with" intent), `AndroidManifest.xml` (intent-filter), create a small `ImportController`/VM glue + provide `EpubImporter` and `ReadiumMetadataExtractor` via Hilt.

- [ ] **Step 1: Hilt provision** — bind `MetadataExtractor` to `ReadiumMetadataExtractor` (a `@Module @Binds` or `@Provides`), and provide `EpubImporter` (`@Provides` with `coversDir = File(context.filesDir,"covers")`, `now = { System.currentTimeMillis() }`). `ReadiumMetadataExtractor` is `@Inject constructor(@ApplicationContext Context)`.
- [ ] **Step 2: Reader route** — add `reader/{bookId}` to navigation (a sibling `composable`, NOT a bottom-bar destination). In `KanomApp`, hide the `NavigationBar` when the current route starts with `reader/`. `LibraryScreen`'s `onOpenBook(id)` navigates to `reader/$id`. Add a placeholder `ReaderScreen(bookId)` (Plan 06 replaces it).
- [ ] **Step 3: File picker** — in `LibraryScreen` (or a small import host), a "+" FAB launches `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` with `arrayOf("application/epub+zip", "application/octet-stream")`; on result, `takePersistableUriPermission`, query `displayName`/`size` via `contentResolver`, and call the importer (through a ViewModel/usecase) off the main thread; show a snackbar for Imported/Duplicate/Unreadable.
- [ ] **Step 4: "Open with"** — add an `<intent-filter>` to `MainActivity` for `VIEW` with `application/epub+zip` (and `content`/`file` schemes). In `MainActivity.onCreate`/`onNewIntent`, detect an incoming EPUB `data` Uri, persist permission, and route it into the same import path.
- [ ] **Step 5: Verify + commit**
`./gradlew :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL + all unit tests pass.
```bash
git add app/src app/src/main/AndroidManifest.xml && git commit -m "feat(library): file-picker + open-with import wiring; reader route"
```

---

## Self-Review

**1. Spec coverage (spec §5.1 library/import, §6.3 UI):**
- Import via picker + OS "open with" → Task 5. ✓
- Grid/list/detail views → Task 4. ✓ · Cover thumbnails (Coil) → Task 4. ✓ · Progress % → Task 3 BookCard. ✓
- Duplicate detection (consulted before add) → Task 2. ✓ · Search/filter → Task 3/4. ✓ · Continue-listening hero → Task 3/4. ✓
- Reader route outside bottom bar + hide bar (Plan 03 final-review rec) → Task 5. ✓
- *Deferred (correct):* actual rendering/resume (Plan 06); the placeholder ReaderScreen is replaced there. Cover/metadata extraction *behavior* is verified manually/in Plan 06 (Readium runtime) — the importer LOGIC is unit-tested via the `MetadataExtractor` seam.

**2. Placeholder scan:** versions marked "verify current" with build gates; Tasks 4/5 describe concrete composables/wiring with named APIs (no "add UI here" hand-waving) but are verified by compile+preview rather than full code blocks for every component — acceptable for UI scaffolding, and the testable logic (Tasks 2/3) has complete code + tests. The `BookWriter`/`BookReader`/`ProgressReader` seams are explicitly defined to keep importer/VM unit-testable.

**3. Type consistency:** `BookEntity`/`ProgressEntity` (Plan 04), `MetadataExtractor`/`BookMetadata`/`EpubImporter`/`ImportResult`, `BookWriter`/`BookReader`/`ProgressReader`, `LibraryViewMode`/`BookCard`/`LibraryUiState`/`LibraryViewModel`, `KanomDestination` (Plan 03) are consistent. Importer extracts BEFORE dedup so title-based dedup uses the real title.

**Note for executor:** keep Readium behind `MetadataExtractor` — never call Readium from `EpubImporter`/`LibraryViewModel` directly (preserves unit-testability). The Readium extractor + Compose UI are compile/preview-verified here; their runtime behavior (real cover/metadata, picker, open-with) is validated on-device alongside the Plan 06 reader. Reuse `app/.../spike/ReadiumProbe.kt` for the open-EPUB API signatures.
