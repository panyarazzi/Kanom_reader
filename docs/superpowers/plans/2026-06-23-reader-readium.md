# Reader (Readium) Implementation Plan — Plan 06

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fill `ReaderScreen(bookId)` with a working Readium reflowable-EPUB reader — render, exact resume, full display settings (themes, fonts, escape hatches, Thai word-break injection), bookmarks with in-page Decoration outlines, and a debug-only sentence-highlight de-risk harness — hosted in-Compose.

**Architecture:** A shared `reader.engine` layer (Hilt-singleton Readium clients + `PublicationProvider` open/close helper + `LocatorSerializer`) refactors the Plan 05 metadata extractor and feeds a `ReaderViewModel`. `MainActivity` becomes a `FragmentActivity`; `EpubNavigatorFragment` is hosted inside the existing `reader/{bookId}` Compose route via `AndroidView` + `FragmentContainerView`, with Compose chrome drawn over it. Pure logic (Locator JSON, prefs mapping, Thai word tokenizer, ZWSP injector, bookmark/throttle helpers) is TDD'd on the JVM/Robolectric; Readium rendering is validated on-device (no EPUB fixture / mocking lib exists, matching how the extractor is tested today).

**Tech Stack:** Kotlin (AGP-9 built-in 2.2.10), Jetpack Compose (BOM 2026.06.00 / M3 1.4.0), Hilt+KSP, Readium 3.3.0 (`readium-shared`/`-streamer`/`-navigator`), Room 2.8.4, DataStore 1.2.1, ICU4J 76.1 (`:text:pipeline`), jsoup (new, XHTML transform), `material-icons-extended` (new). Tests: JUnit4, Robolectric 4.16.1 `@Config(sdk=[35])`, Turbine, coroutines-test.

**Verified API reference:** `docs/superpowers/spikes/2026-06-23-readium-api-recon.md` (cite for exact signatures). **Spec:** `docs/superpowers/specs/2026-06-23-reader-readium-design.md`.

## Global Constraints

- **Package:** all new reader code under `com.nextz.kanomtts.reader` (a package in `:app`; module-collapse decision stands). New `:text:pipeline` code stays in `com.nextz.kanomtts.text.pipeline`.
- **No Kotlin plugin in `:app`** (AGP-9 built-in Kotlin compiles `src/main/kotlin`). `:text:pipeline` uses the standalone `kotlin.jvm` plugin pinned `2.1.0`, `jvmToolchain(11)`, deps `icu4j` + `junit` only.
- **Readium opt-ins:** annotate reader files `@OptIn(ExperimentalReadiumApi::class)` (`org.readium.r2.shared.ExperimentalReadiumApi`). `@OptIn(InternalReadiumApi::class)` only for the Thai-transform `Publication(container=...)` rebuild. Resolve `ReadError` / `AttributesHolder` import packages via IDE auto-import (recon §8 items 3, 8).
- **`publisherStyles=false` GATE:** `fontSize`/`lineHeight`/`pageMargins`/`paragraphSpacing`/`textAlign` only take effect when `publisherStyles=false`. NOT gated: `theme`, `fontFamily`, `textColor`, `backgroundColor`. (recon §2)
- **Decoration group semantics:** `applyDecorations(list, group)` REPLACES the whole named group (Readium diffs); clear with `emptyList()`. `tint` is `@ColorInt Int` (`color.toArgb()`). `applyDecorations` is **suspend**; apply after first locator callback. (recon §3)
- **Fonts:** TTFs MUST be copied to `app/src/main/assets/fonts/` (the `res/font` copies are Compose-only, unreachable by the WebView); `servedAssets += "fonts/.*"` is MANDATORY; `FontFamily("Sarabun")` name string must match between declaration and `EpubPreferences.fontFamily`. (recon §4)
- **Thai ZWSP:** inject `U+200B` via a `TransformingContainer` `EntryTransformer` on `.xhtml`/`.html`; keep a SEPARATE un-transformed `Publication` for the `Content`/TTS pipeline (Plan 07). (recon §6)
- **Two `Configuration` types:** `EpubNavigatorFactory.Configuration` (small) vs `EpubNavigatorFragment.Configuration` (DSL-built). Use the DSL form for the fragment config. (recon §7)
- **Resume position** is passed via `createFragmentFactory(initialLocator = …)`, NOT a method call. ToC is a `val tableOfContents: List<Link>` (not suspend, not a function). (recon §1)
- **No Room schema change** (all entity fields exist). New display toggles are DataStore keys only.
- **Commit cadence:** every task ends with a green test/compile gate + a commit. Branch is `feat/reader-readium` (already created; spec committed at `71074ae`).

---

## File Structure

**Create**
- `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ReadiumModule.kt` — Hilt singletons (HttpClient/AssetRetriever/PublicationOpener).
- `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/PublicationProvider.kt` — `open(uri)` + `withPublication(uri){}`.
- `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/LocatorSerializer.kt` — Locator ⇄ JSON String.
- `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/EpubPreferencesMapper.kt` — `KanomPreferences → EpubPreferences`.
- `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ThaiZwspInjector.kt` — XHTML U+200B injection (jsoup + WordTokenizer).
- `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderUiState.kt`, `ReaderViewModel.kt`, `ReaderScreen.kt`, `ReaderChrome.kt`, `ReaderTocSheet.kt`, `ReaderBookmarksSheet.kt`, `ReaderSettingsSheet.kt`.
- `app/src/main/kotlin/com/nextz/kanomtts/reader/decoration/BookmarkDecorations.kt`, `SentenceHighlightProbe.kt`.
- `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderBookmarkFactory.kt` — pure `buildBookmark(...)`.
- `app/src/main/kotlin/com/nextz/kanomtts/data/repository/ProgressWriter.kt`, `BookmarkStore.kt` — new seams.
- `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/WordTokenizer.kt`.
- `app/src/main/assets/fonts/` — `sarabun_regular.ttf`, `sarabun_medium.ttf`, `sarabun_semibold.ttf`, `sarabun_bold.ttf` (copies of the `res/font` files).
- Tests: `reader/engine/LocatorSerializerTest.kt`, `EpubPreferencesMapperTest.kt`, `ThaiZwspInjectorTest.kt`; `reader/ReaderViewModelTest.kt`, `ReaderBookmarkFactoryTest.kt`; `reader/util/Fakes.kt`; `text/pipeline/.../WordTokenizerTest.kt`.

**Modify**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — add `material-icons-extended`, `jsoup`.
- `app/src/main/kotlin/com/nextz/kanomtts/MainActivity.kt` — `ComponentActivity → FragmentActivity`.
- `app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt` — reader route wires `ReaderViewModel` + `onBack`.
- `app/src/main/kotlin/com/nextz/kanomtts/ui/screens/ReaderScreen.kt` — delete placeholder (moved to `reader/ReaderScreen.kt`).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/library/ReadiumMetadataExtractor.kt` — consume `PublicationProvider`.
- `app/src/main/kotlin/com/nextz/kanomtts/data/repository/BookReader.kt`, `BookRepository.kt`, `ProgressReader.kt`, `ProgressRepository.kt`, `BookmarkRepository.kt` — seam extensions.
- `app/src/main/kotlin/com/nextz/kanomtts/data/di/RepositoryBindings.kt` — bind `ProgressWriter`, `BookmarkStore`.
- `app/src/main/kotlin/com/nextz/kanomtts/data/prefs/KanomPreferences.kt`, `PreferencesRepository.kt` — escape-hatch + thaiWordBreak keys.
- `app/src/test/kotlin/com/nextz/kanomtts/feature/library/util/Fakes.kt` — `override` the extended seam methods.
- `app/src/main/kotlin/com/nextz/kanomtts/feature/library/LibraryComponents.kt` + `LibraryUiState.kt` — drop `DETAIL` from the view-mode toggle (Task 9).

---

## Task 1: Readium engine layer + LocatorSerializer

**Files:**
- Create: `reader/engine/ReadiumModule.kt`, `reader/engine/PublicationProvider.kt`, `reader/engine/LocatorSerializer.kt`
- Modify: `feature/library/ReadiumMetadataExtractor.kt`
- Test: `reader/engine/LocatorSerializerTest.kt`

**Interfaces:**
- Produces: `PublicationProvider.open(uri: String): Publication?`, `PublicationProvider.withPublication(uri: String, block: suspend (Publication) -> T): T?`; `object LocatorSerializer { fun encode(Locator): String; fun decode(String?): Locator? }`.
- Consumes: `AssetRetriever`, `PublicationOpener` (provided by `ReadiumModule`).

- [ ] **Step 1: Write the failing test** — `app/src/test/kotlin/com/nextz/kanomtts/reader/engine/LocatorSerializerTest.kt`

```kotlin
package com.nextz.kanomtts.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class LocatorSerializerTest {
    private fun sample() = Locator(
        href = Url("chapter1.xhtml")!!,
        mediaType = MediaType.XHTML,
        title = "Chapter 1",
        locations = Locator.Locations(progression = 0.5, totalProgression = 0.25, position = 3),
        text = Locator.Text(before = "before ", highlight = "the spoken sentence", after = " after"),
    )

    @Test fun round_trips_through_json() {
        val original = sample()
        val restored = LocatorSerializer.decode(LocatorSerializer.encode(original))
        assertEquals(original, restored)
    }

    @Test fun decode_null_is_null() {
        assertNull(LocatorSerializer.decode(null))
    }

    @Test fun decode_garbage_is_null() {
        assertNull(LocatorSerializer.decode("not json"))
    }
}
```

- [ ] **Step 2: Run it — verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.reader.engine.LocatorSerializerTest"`
Expected: FAIL — `Unresolved reference: LocatorSerializer`.

- [ ] **Step 3: Implement `LocatorSerializer`** — `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/LocatorSerializer.kt`

```kotlin
package com.nextz.kanomtts.reader.engine

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Serialize a Readium [Locator] to/from the JSON String stored in Room (ProgressEntity/BookmarkEntity). */
object LocatorSerializer {
    fun encode(locator: Locator): String = locator.toJSON().toString()

    fun decode(json: String?): Locator? =
        json?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
}
```

- [ ] **Step 4: Run it — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.reader.engine.LocatorSerializerTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Implement `ReadiumModule`** — `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ReadiumModule.kt`

```kotlin
package com.nextz.kanomtts.reader.engine

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import javax.inject.Singleton

/** Single Readium open-pipeline shared by the metadata extractor and the reader (Plan 05 debt: was per-call). */
@Module
@InstallIn(SingletonComponent::class)
object ReadiumModule {
    @Provides @Singleton
    fun httpClient(): DefaultHttpClient = DefaultHttpClient()

    @Provides @Singleton
    fun assetRetriever(@ApplicationContext ctx: Context, http: DefaultHttpClient): AssetRetriever =
        AssetRetriever(contentResolver = ctx.contentResolver, httpClient = http)

    @Provides @Singleton
    fun publicationOpener(
        @ApplicationContext ctx: Context,
        http: DefaultHttpClient,
        assetRetriever: AssetRetriever,
    ): PublicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = ctx,
            httpClient = http,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )
}
```

- [ ] **Step 6: Implement `PublicationProvider`** — `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/PublicationProvider.kt`

```kotlin
package com.nextz.kanomtts.reader.engine

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import javax.inject.Inject

/**
 * Opens EPUBs through the shared Readium pipeline. The reader holds the [Publication] for the
 * screen lifetime ([open] — caller closes); the extractor uses [withPublication] (open/use/close).
 */
class PublicationProvider @Inject constructor(
    private val assetRetriever: AssetRetriever,
    private val opener: PublicationOpener,
) {
    /** Opens [uri]; caller owns [Publication.close]. Returns null if the EPUB cannot be opened. */
    suspend fun open(uri: String): Publication? = withContext(Dispatchers.IO) {
        val url = Uri.parse(uri).toAbsoluteUrl() ?: return@withContext null
        val asset = assetRetriever.retrieve(url).getOrElse { return@withContext null }
        opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            return@withContext null
        }
    }

    /** Opens [uri], runs [block], and guarantees [Publication.close]. Returns null if open fails. */
    suspend fun <T> withPublication(uri: String, block: suspend (Publication) -> T): T? {
        val publication = open(uri) ?: return null
        return try {
            block(publication)
        } finally {
            publication.close()
        }
    }
}
```

- [ ] **Step 7: Refactor `ReadiumMetadataExtractor` onto `PublicationProvider`** — replace the whole class body of `feature/library/ReadiumMetadataExtractor.kt` (keep the file's KDoc header; update it to note it now delegates opening to `PublicationProvider`):

```kotlin
package com.nextz.kanomtts.feature.library

import android.graphics.Bitmap
import com.nextz.kanomtts.reader.engine.PublicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * [MetadataExtractor] backed by Readium 3.3.0. Opening/closing is delegated to the shared
 * [PublicationProvider] (Plan 06 — replaces this class's former per-call DefaultHttpClient/
 * AssetRetriever construction). Reads title/author/lang + PNG cover bytes; null on open failure.
 */
class ReadiumMetadataExtractor @Inject constructor(
    private val publications: PublicationProvider,
) : MetadataExtractor {

    override suspend fun extract(uri: String): BookMetadata? = withContext(Dispatchers.IO) {
        publications.withPublication(uri) { publication ->
            val metadata = publication.metadata
            val title = metadata.title
                ?: uri.substringAfterLast('/').removeSuffix(".epub").ifBlank { "Unknown" }
            val author = metadata.authors.firstOrNull()?.name
            val lang = metadata.languages.firstOrNull()
            val coverBytes = publication.cover()?.let { bitmap ->
                ByteArrayOutputStream().also { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }.toByteArray()
            }
            BookMetadata(title = title, author = author, lang = lang, coverBytes = coverBytes)
        }
    }
}
```

(No `LibraryModule.kt` change: the `@Binds metadataExtractor(impl: ReadiumMetadataExtractor)` still resolves — `ReadiumMetadataExtractor`'s `@Inject` constructor now takes `PublicationProvider`, itself provided via `ReadiumModule`.)

- [ ] **Step 8: Verify the module compiles + all existing tests still pass**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `LocatorSerializerTest` + all prior suites green (the extractor refactor is source-compatible; its open path remains device-validated).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/reader/engine app/src/test/kotlin/com/nextz/kanomtts/reader/engine app/src/main/kotlin/com/nextz/kanomtts/feature/library/ReadiumMetadataExtractor.kt
git commit -m "feat(reader): Readium engine singletons + PublicationProvider + LocatorSerializer; refactor extractor"
```

---

## Task 2: FragmentActivity + first runnable bare render (harness milestone)

**Files:**
- Modify: `MainActivity.kt`, `ui/navigation/KanomApp.kt`, `data/repository/BookReader.kt`, `data/repository/BookRepository.kt`, `feature/library/util/Fakes.kt`
- Delete: `ui/screens/ReaderScreen.kt` (placeholder)
- Create: `reader/ReaderUiState.kt`, `reader/ReaderViewModel.kt`, `reader/ReaderScreen.kt`
- Test: `reader/ReaderViewModelTest.kt`, `reader/util/Fakes.kt`

**Interfaces:**
- Consumes: `PublicationProvider.open`, `LocatorSerializer.decode`, `BookReader.get`, `ProgressReader.observe`.
- Produces: `ReaderViewModel(bookId)` exposing `uiState: StateFlow<ReaderUiState>`; `ReaderScreen(bookId, onBack)`; `BookReader.get(id): BookEntity?`.

- [ ] **Step 1: Extend `BookReader` with `get`** — add to `data/repository/BookReader.kt`:

```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookEntity
import kotlinx.coroutines.flow.Flow

interface BookReader {
    fun observeLibrary(): Flow<List<BookEntity>>
    suspend fun get(id: String): BookEntity?
}
```

In `data/repository/BookRepository.kt` mark the existing `get` as `override`:

```kotlin
    override suspend fun get(id: String): BookEntity? = dao.getById(id)
```

In `app/src/test/kotlin/com/nextz/kanomtts/feature/library/util/Fakes.kt`, mark `FakeBookRepository.get` as `override`:

```kotlin
    override suspend fun get(id: String): BookEntity? = books.value.find { it.id == id }
```

- [ ] **Step 2: Write the failing VM test** — `app/src/test/kotlin/com/nextz/kanomtts/reader/ReaderViewModelTest.kt`

```kotlin
package com.nextz.kanomtts.reader

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.reader.util.FakeReaderBookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var source: FakeReaderBookSource

    @Before fun setUp() { Dispatchers.setMain(dispatcher); source = FakeReaderBookSource() }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(bookId: String) = ReaderViewModel(bookId, source)

    @Test fun `missing book yields Error state`() = runTest {
        source.book = null
        val vm = vm("missing")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value is ReaderUiState.Error)
    }

    @Test fun `failed publication open yields Error state`() = runTest {
        source.book = BookEntity("1", "content://x", "T", null, null, null, 1L, 1L, null)
        source.publication = null // open fails
        val vm = vm("1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertTrue(vm.uiState.value is ReaderUiState.Error)
    }
}
```

> The `Ready` branch (real `Publication`) is not unit-tested — no EPUB fixture / mocking lib exists; it is device-validated in Step 9. `FakeReaderBookSource` lets us drive the error branches via a seam (`ReaderBookSource`) that the VM depends on instead of `PublicationProvider` directly, so the VM's pre-render state machine is testable.

- [ ] **Step 3: Run it — verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.reader.ReaderViewModelTest"`
Expected: FAIL — unresolved `ReaderViewModel` / `ReaderUiState` / `FakeReaderBookSource`.

- [ ] **Step 4: Create `ReaderUiState`** — `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderUiState.kt`

```kotlin
package com.nextz.kanomtts.reader

import androidx.fragment.app.FragmentFactory
import com.nextz.kanomtts.data.db.BookEntity
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Ready(
        val book: BookEntity,
        val publication: Publication,
        val fragmentFactory: FragmentFactory,
        val chromeVisible: Boolean = true,
    ) : ReaderUiState
}
```

- [ ] **Step 5: Create the `ReaderBookSource` seam + impl + fake**

`app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderBookSource.kt`:

```kotlin
package com.nextz.kanomtts.reader

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.reader.engine.PublicationProvider
import org.readium.r2.shared.publication.Publication
import javax.inject.Inject

/** Seam the ReaderViewModel depends on, so its pre-render state machine is unit-testable. */
interface ReaderBookSource {
    suspend fun book(bookId: String): BookEntity?
    suspend fun open(uri: String): Publication?
}

class DefaultReaderBookSource @Inject constructor(
    private val books: BookReader,
    private val publications: PublicationProvider,
) : ReaderBookSource {
    override suspend fun book(bookId: String): BookEntity? = books.get(bookId)
    override suspend fun open(uri: String): Publication? = publications.open(uri)
}
```

Bind it — add to `data/di/RepositoryBindings.kt` (import `com.nextz.kanomtts.reader.*`):

```kotlin
    @Binds @Singleton abstract fun readerBookSource(impl: com.nextz.kanomtts.reader.DefaultReaderBookSource): com.nextz.kanomtts.reader.ReaderBookSource
```

`app/src/test/kotlin/com/nextz/kanomtts/reader/util/Fakes.kt`:

```kotlin
package com.nextz.kanomtts.reader.util

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.reader.ReaderBookSource
import org.readium.r2.shared.publication.Publication

class FakeReaderBookSource : ReaderBookSource {
    var book: BookEntity? = null
    var publication: Publication? = null
    override suspend fun book(bookId: String): BookEntity? = book
    override suspend fun open(uri: String): Publication? = publication
}
```

- [ ] **Step 6: Create `ReaderViewModel`** — `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt`

```kotlin
package com.nextz.kanomtts.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import javax.inject.Inject

@OptIn(ExperimentalReadiumApi::class)
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @Inject constructor(
    private val bookId: String,
    private val source: ReaderBookSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var publicationToClose: org.readium.r2.shared.publication.Publication? = null

    init { load() }

    private fun load() = viewModelScope.launch {
        val book = source.book(bookId)
        if (book == null) { _uiState.value = ReaderUiState.Error("Book not found"); return@launch }
        val publication = source.open(book.uri)
        if (publication == null) { _uiState.value = ReaderUiState.Error("Could not open this EPUB"); return@launch }
        publicationToClose = publication
        val factory = EpubNavigatorFactory(publication)
            .createFragmentFactory(initialLocator = null)   // resume + listener wired in Task 3
        _uiState.value = ReaderUiState.Ready(book = book, publication = publication, fragmentFactory = factory)
    }

    fun setChromeVisible(visible: Boolean) = _uiState.update {
        if (it is ReaderUiState.Ready) it.copy(chromeVisible = visible) else it
    }

    override fun onCleared() {
        publicationToClose?.close()
        publicationToClose = null
    }

    @dagger.assisted.AssistedFactory
    interface Factory { fun create(@dagger.assisted.Assisted bookId: String): ReaderViewModel }
}
```

> The `@Inject`-with-`bookId` + assisted factory shape: the implementer wires `bookId` via the assisted factory (Hilt 2.59.2 `@HiltViewModel(assistedFactory=…)`). If the assisted-VM wiring proves noisy, the fallback is reading `bookId` from `SavedStateHandle` (the route arg) in a plain `@HiltViewModel` and dropping the `Factory` — both are acceptable; pick one and keep the test constructor `ReaderViewModel(bookId, source)` working. Confirm the assisted-injection compiles (`./gradlew :app:kspDebugKotlin`).

- [ ] **Step 7: Run the VM test — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.reader.ReaderViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 8: Switch `MainActivity` to `FragmentActivity` + host the fragment in `ReaderScreen`**

Edit `MainActivity.kt` — change the base class and import:

```kotlin
import androidx.fragment.app.FragmentActivity
// ...
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
```
(everything else in `MainActivity` is unchanged — `setContent`, `viewModels`, intent handling all work on `FragmentActivity`.)

Delete `app/src/main/kotlin/com/nextz/kanomtts/ui/screens/ReaderScreen.kt`.

Create `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderScreen.kt`:

```kotlin
package com.nextz.kanomtts.reader

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.compose.rememberSaveable     // or androidx.compose.runtime.saveable.rememberSaveable
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is ReaderUiState.Loading -> CircularProgressIndicator()
            is ReaderUiState.Error -> Text(s.message, style = MaterialTheme.typography.titleMedium)
            is ReaderUiState.Ready -> ReaderContent(bookId = bookId, state = s)
        }
    }
    // Chrome (top bar w/ onBack, sheets) is layered here in later tasks.
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderContent(bookId: String, state: ReaderUiState.Ready) {
    val activity = LocalContext.current as FragmentActivity
    val containerId = rememberSaveable { View.generateViewId() }
    val tag = "reader-$bookId"
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
        update = {
            val fm = activity.supportFragmentManager
            if (fm.findFragmentByTag(tag) == null) {
                fm.fragmentFactory = state.fragmentFactory
                fm.commit { add(containerId, EpubNavigatorFragment::class.java, Bundle(), tag) }
            }
        },
    )
}
```

> **Device-validation gate (recon §8 item 7):** confirm the `FragmentContainerView` id survives recomposition/config-change and the fragment isn't double-committed. If `rememberSaveable { View.generateViewId() }` regenerates, switch to a fixed id constant in `ids.xml`. Adjust `rememberSaveable` import to whichever the Compose BOM exposes.

- [ ] **Step 9: Wire the route** — in `ui/navigation/KanomApp.kt`, replace the reader `composable` block:

```kotlin
            composable("reader/{bookId}") { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val vm: com.nextz.kanomtts.reader.ReaderViewModel = androidx.hilt.navigation.compose.hiltViewModel(
                    creationCallback = { factory: com.nextz.kanomtts.reader.ReaderViewModel.Factory -> factory.create(bookId) },
                )
                com.nextz.kanomtts.reader.ReaderScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                    viewModel = vm,
                )
            }
```
Update the import at the top: remove `import com.nextz.kanomtts.ui.screens.ReaderScreen`. (If the assisted-factory fallback from Task 2 Step 6 was chosen, use plain `hiltViewModel()` and the route arg via `SavedStateHandle`.)

- [ ] **Step 10: Build, run on device — the harness milestone**

Run: `./gradlew :app:assembleDebug` → Expected: BUILD SUCCESSFUL.
Device: install, import a real Thai EPUB (Library FAB), tap it → the reflowable EPUB renders full-screen; back returns to Library. (First on-device reader — eyeballing starts here.)

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat(reader): FragmentActivity host + ReaderViewModel + first runnable EPUB render"
```

---

## Task 3: Exact resume — load initial locator + debounced save

**Files:**
- Create: `data/repository/ProgressWriter.kt`
- Modify: `data/repository/ProgressReader.kt`, `ProgressRepository.kt`, `data/di/RepositoryBindings.kt`, `feature/library/util/Fakes.kt`, `reader/ReaderBookSource.kt`, `reader/ReaderViewModel.kt`, `reader/ReaderUiState.kt`
- Test: `reader/ReaderViewModelTest.kt` (extend)

**Interfaces:**
- Produces: `ProgressReader.get(bookId): ProgressEntity?`; `ProgressWriter.save(ProgressEntity)`; `ReaderViewModel.onLocatorChanged(Locator)` (debounced persist); `Ready.initialLocator: Locator?`.
- Consumes: `LocatorSerializer`, `BookWriter.add` (for `lastOpenedAt`).

- [ ] **Step 1: Extend the progress seams.** Add `suspend fun get(bookId: String): ProgressEntity?` to `ProgressReader.kt`; create `ProgressWriter.kt`:

```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ProgressEntity

interface ProgressWriter {
    suspend fun save(progress: ProgressEntity)
}
```
Mark `ProgressRepository.get`/`.save` as `override` (and add `, ProgressWriter` to its supertypes). Bind in `RepositoryBindings.kt`:
```kotlin
    @Binds @Singleton abstract fun progressWriter(impl: ProgressRepository): ProgressWriter
```
In `feature/library/util/Fakes.kt`, add to `FakeProgressRepository`: `override suspend fun get(bookId: String): ProgressEntity? = progressMap.value[bookId]`.

- [ ] **Step 2: Write failing test — initial locator + debounced save** (append to `ReaderViewModelTest.kt`; extend `FakeReaderBookSource` with a `savedProgress` capture and a stored locator). Test that two rapid `onLocatorChanged` calls within the debounce window persist once with the latest locator, and that `Ready.initialLocator` reflects stored progress. (Use `advanceTimeBy`/`runCurrent` from coroutines-test for the debounce window.)

```kotlin
    @Test fun `rapid locator changes persist once with the latest`() = runTest {
        source.book = BookEntity("1", "content://x", "T", null, null, null, 1L, 1L, null)
        source.publication = fakePublication()      // see note
        val vm = vm("1")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onLocatorChanged(locatorA); vm.onLocatorChanged(locatorB)
        advanceTimeBy(1_100); runCurrent()
        assertEquals(1, source.saveCount)
        assertEquals(LocatorSerializer.encode(locatorB), source.lastSavedProgress?.locator)
    }
```
> Because the `Ready` path needs a `Publication`, this test requires a constructable Locator (Task 1 sample) but NOT a real Publication if you keep the debounce/save logic in a method that takes a `Locator` and writes via the `ReaderBookSource` seam (extend the seam with `saveProgress`). Keep the save/throttle logic seam-driven so it is testable without a Publication; if a Publication is unavoidable, drop this to a device-validated step and instead unit-test a pure `ProgressThrottle` helper. **Implementer: prefer extracting `onLocatorChanged` debounce into the VM using `viewModelScope` + a `MutableSharedFlow<Locator>.debounce(800).onEach { save }` and test the throttle via virtual time.**

- [ ] **Step 3: Run — verify it fails.** `./gradlew :app:testDebugUnitTest --tests "*ReaderViewModelTest*"` → FAIL.

- [ ] **Step 4: Implement resume + debounce.** Extend `ReaderBookSource`/`DefaultReaderBookSource` with `suspend fun progress(bookId): ProgressEntity?`, `suspend fun saveProgress(ProgressEntity)`, `suspend fun touchOpened(BookEntity)` (the last does `books.add(book.copy(lastOpenedAt = now))` via an injected `now: () -> Long` and `BookWriter`). Add `initialLocator: Locator?` to `Ready`. In the VM: after open, read `source.progress(bookId)`, decode `initialLocator`, pass it to `createFragmentFactory(initialLocator = initialLocator, listener = …)`, call `touchOpened`. Add:

```kotlin
    private val locatorChanges = MutableSharedFlow<org.readium.r2.shared.publication.Locator>(extraBufferCapacity = 1)
    init {
        load()
        viewModelScope.launch {
            locatorChanges.debounce(800).collect { loc ->
                source.saveProgress(ProgressEntity(
                    bookId = bookId,
                    locator = LocatorSerializer.encode(loc),
                    ttsLocator = source.progress(bookId)?.ttsLocator,   // preserve Plan 07's field
                    percent = (loc.locations.totalProgression ?: 0.0).toFloat(),
                    updatedAt = now(),
                ))
            }
        }
    }
    fun onLocatorChanged(locator: Locator) { locatorChanges.tryEmit(locator) }
```
(`@OptIn(FlowPreview::class)` for `debounce`.) The composable wires `navigator.currentLocator.onEach { vm.onLocatorChanged(it) }.launchIn(...)` once the navigator is obtained (extend `ReaderContent` to find the committed fragment and `LaunchedEffect` collect its `currentLocator`). Make the fragment factory carry the `EpubNavigatorFragment.Listener` (a minimal listener overriding `onExternalLinkActivated`).

- [ ] **Step 5: Run — verify it passes.** `./gradlew :app:testDebugUnitTest --tests "*ReaderViewModelTest*"` → PASS.

- [ ] **Step 6: Device-validate resume.** Build, open a book, scroll mid-chapter, leave, reopen → lands on the same position. Confirm `percent` updates the Library progress ring.

- [ ] **Step 7: Commit.** `git add -A && git commit -m "feat(reader): exact resume — initial locator + debounced progress save"`

---

## Task 4: Table of Contents + navigation

**Files:** Create `reader/ReaderTocSheet.kt`; Modify `reader/ReaderUiState.kt` (add `toc: List<Link>`), `reader/ReaderViewModel.kt` (populate `toc = publication.tableOfContents`), `reader/ReaderScreen.kt` (ToC button + sheet + `navigator.go(link)`).

- [ ] **Step 1:** Add `toc: List<Link>` to `Ready` (`org.readium.r2.shared.publication.Link`); in the VM `load()`, set `toc = publication.tableOfContents` (a **val**, not a function — recon §1). No unit test (pure Readium data); device-validated.
- [ ] **Step 2:** Create `ReaderTocSheet` — a `ModalBottomSheet` listing `toc` entries (`Link.title`), each row `onClick { onNavigate(link) }`. Follow `LibraryComponents.kt` list-row styling.
- [ ] **Step 3:** In `ReaderScreen`, hold the obtained `EpubNavigatorFragment` (from Task 3's bind), add a ToC action to chrome that opens the sheet; on row click call `navigator.go(link, animated = true)` and close the sheet.
- [ ] **Step 4:** Build + device-validate: ToC opens, tapping a chapter jumps to it. `./gradlew :app:assembleDebug` → SUCCESS.
- [ ] **Step 5:** Commit. `git commit -am "feat(reader): table of contents sheet + chapter navigation"`

---

## Task 5: Display settings (core) — themes/fonts/size/line-height/margins, live

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (add `material-icons-extended`), `data/prefs/KanomPreferences.kt`, `PreferencesRepository.kt`, `reader/ReaderViewModel.kt`, `reader/ReaderScreen.kt`
- Create: `reader/engine/EpubPreferencesMapper.kt`, `reader/ReaderSettingsSheet.kt`, `app/src/main/assets/fonts/*.ttf`
- Test: `reader/engine/EpubPreferencesMapperTest.kt`, `data/prefs/PreferencesRepositoryTest.kt` (extend)

**Interfaces:**
- Produces: `EpubPreferencesMapper.map(KanomPreferences): EpubPreferences`; `ReaderViewModel.applyPreferences(KanomPreferences)`; settings setters delegating to `PreferencesRepository`.

- [ ] **Step 1: Add deps.** In `libs.versions.toml` `[libraries]`:
```toml
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
```
In `app/build.gradle.kts` after the icons-core line:
```kotlin
    implementation(libs.androidx.compose.material.icons.extended)
```
(R8 tree-shakes unused icons in release; debug-only size cost.)

- [ ] **Step 2: Copy Sarabun TTFs to assets.** `mkdir -p app/src/main/assets/fonts` and copy the four `app/src/main/res/font/sarabun_*.ttf` files there (same bytes; the WebView cannot read `res/font`). Verify: `ls app/src/main/assets/fonts` shows `sarabun_regular.ttf`, `sarabun_medium.ttf`, `sarabun_semibold.ttf`, `sarabun_bold.ttf`.

- [ ] **Step 3: Write the failing mapper test** — `app/src/test/kotlin/com/nextz/kanomtts/reader/engine/EpubPreferencesMapperTest.kt`

```kotlin
package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
class EpubPreferencesMapperTest {
    @Test fun `true black maps to DARK theme with black background`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readerTheme = ReaderTheme.TRUE_BLACK))
        assertEquals(Theme.DARK, p.theme)
        assertEquals(android.graphics.Color.BLACK, p.backgroundColor!!.int)
    }
    @Test fun `sarabun maps to named family`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readingFont = ReadingFont.SARABUN))
        assertEquals(FontFamily("Sarabun"), p.fontFamily)
    }
    @Test fun `system font with forceFont off leaves family null`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readingFont = ReadingFont.SYSTEM), forceFont = false)
        assertNull(p.fontFamily)
    }
    @Test fun `font size maps to ratio over 16`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(fontSizeSp = 24))
        assertEquals(24.0 / 16.0, p.fontSize!!, 1e-9)
    }
    @Test fun `usePublisherStyles false by default so typography applies`() {
        assertEquals(false, EpubPreferencesMapper.map(KanomPreferences()).publisherStyles)
    }
}
```

- [ ] **Step 4: Run — verify it fails.** `./gradlew :app:testDebugUnitTest --tests "*EpubPreferencesMapperTest"` → FAIL (unresolved mapper). If `org.readium.r2.navigator.preferences.*` classes fail to load on the plain JVM, add `@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])` (they are pure value/enum types, so plain JUnit is expected to work).

- [ ] **Step 5: Implement the mapper** — `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/EpubPreferencesMapper.kt`

```kotlin
package com.nextz.kanomtts.reader.engine

import android.graphics.Color as AndroidColor
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.ReaderTheme
import com.nextz.kanomtts.ui.theme.onSurface
import com.nextz.kanomtts.ui.theme.surface
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as RColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language
import androidx.compose.ui.graphics.toArgb

/** Pure mapping from Kanom display prefs to Readium EpubPreferences. (recon §2) */
@OptIn(ExperimentalReadiumApi::class)
object EpubPreferencesMapper {

    private const val BASE_FONT_SP = 16.0
    private const val BASE_MARGIN_DP = 16.0

    fun map(
        prefs: KanomPreferences,
        forceFont: Boolean = true,
        usePublisherStyles: Boolean = false,
    ): EpubPreferences {
        val readiumTheme = when (prefs.readerTheme) {
            ReaderTheme.LIGHT -> Theme.LIGHT
            ReaderTheme.SEPIA -> Theme.SEPIA
            ReaderTheme.DARK -> Theme.DARK
            ReaderTheme.TRUE_BLACK -> Theme.DARK
        }
        val bg = if (prefs.readerTheme == ReaderTheme.TRUE_BLACK) AndroidColor.BLACK
        else prefs.readerTheme.surface().toArgb()
        val fg = prefs.readerTheme.onSurface().toArgb()

        val family = when (prefs.readingFont) {
            ReadingFont.SARABUN -> FontFamily("Sarabun")
            ReadingFont.SERIF -> FontFamily.SERIF
            ReadingFont.SYSTEM -> if (forceFont) null else null
        }

        return EpubPreferences(
            theme = readiumTheme,
            backgroundColor = RColor(bg),
            textColor = RColor(fg),
            fontFamily = family,
            fontSize = prefs.fontSizeSp / BASE_FONT_SP,
            lineHeight = prefs.lineHeight.toDouble(),
            pageMargins = prefs.marginDp / BASE_MARGIN_DP,
            publisherStyles = usePublisherStyles,
            language = Language("th"),
        )
    }
}
```
> Note: `ReadingFont.SYSTEM` yields `null` either way at this core stage; the `forceFont`/`usePublisherStyles` toggles gain real divergence in Task 6 (escape hatches). The test `system font with forceFont off leaves family null` passes now and remains valid.

- [ ] **Step 6: Run — verify it passes.** `./gradlew :app:testDebugUnitTest --tests "*EpubPreferencesMapperTest"` → PASS (5).

- [ ] **Step 7: Apply prefs live + register the font.** In the VM: collect `PreferencesRepository.preferences`, and on each value call the navigator's `submitPreferences(EpubPreferencesMapper.map(prefs))` (the composable forwards a `navigator` handle, or the VM exposes a `currentPreferences: StateFlow<EpubPreferences>` the composable submits). Build the fragment with the font config:
```kotlin
val config = EpubNavigatorFragment.Configuration {
    servedAssets += "fonts/.*"
    addFontFamilyDeclaration(FontFamily("Sarabun")) {
        addFontFace { addSource("fonts/sarabun_regular.ttf"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.NORMAL) }
        addFontFace { addSource("fonts/sarabun_medium.ttf"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.MEDIUM) }
        addFontFace { addSource("fonts/sarabun_semibold.ttf"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.SEMI_BOLD) }
        addFontFace { addSource("fonts/sarabun_bold.ttf"); setFontStyle(FontStyle.NORMAL); setFontWeight(FontWeight.BOLD) }
    }
}
```
pass `configuration = config` + `initialPreferences = EpubPreferencesMapper.map(initialPrefs)` to `createFragmentFactory`.

- [ ] **Step 8: Build the settings sheet.** Create `ReaderSettingsSheet` (`ModalBottomSheet`): theme chips (LIGHT/SEPIA/DARK/TRUE_BLACK), font family (SARABUN/SERIF/SYSTEM), and sliders for size/line-height/margin, each calling a VM setter that writes `PreferencesRepository` (`setReaderTheme`, `setReadingFont`, `setFontSizeSp`, `setLineHeight`, `setMarginDp`). Add a `Tune`/`FormatSize` icon to chrome (now available from `material-icons-extended`). Follow existing Compose styling; reserve rose only for highlight.

- [ ] **Step 9: Device-validate.** Changing theme/font/size/line-height/margins re-renders live; Sarabun visibly applies (if not, the recon §4 fallback: confirm `publisherStyles=false` and the `assets/fonts` copy). `./gradlew :app:assembleDebug` → SUCCESS.

- [ ] **Step 10: Commit.** `git commit -am "feat(reader): live display settings (themes/font/size/line-height/margins) + Sarabun"`

---

## Task 6: Escape hatches + Thai word-break injection

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (add `jsoup`), `data/prefs/KanomPreferences.kt`, `PreferencesRepository.kt`, `reader/engine/EpubPreferencesMapper.kt`, `reader/engine/PublicationProvider.kt`, `reader/ReaderViewModel.kt`, `reader/ReaderSettingsSheet.kt`
- Create: `text/pipeline/.../WordTokenizer.kt`, `reader/engine/ThaiZwspInjector.kt`
- Test: `text/pipeline/.../WordTokenizerTest.kt`, `reader/engine/ThaiZwspInjectorTest.kt`, `EpubPreferencesMapperTest.kt` (extend), `PreferencesRepositoryTest.kt` (extend)

**Interfaces:**
- Produces: `WordTokenizer.tokenize(String): List<IntRange>` (Thai word boundaries); `ThaiZwspInjector.inject(html: String): String`; `EpubPreferencesMapper.map(prefs, forceFont, usePublisherStyles, stripColors)`; `PublicationProvider.openForDisplay(uri, thaiWordBreak): Publication?`.

- [ ] **Step 1: Add `jsoup`.** `libs.versions.toml`: `jsoup = "1.18.3"` under `[versions]`; `jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }` under `[libraries]`. `app/build.gradle.kts`: `implementation(libs.jsoup)`.

- [ ] **Step 2: Write the failing `WordTokenizer` test** — `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/WordTokenizerTest.kt` (plain JUnit, mirrors `SentenceTokenizerTest`):

```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTokenizerTest {
    @Test fun splits_thai_run_into_multiple_words() {
        // "สวัสดีตอนเช้า" (no spaces) should break into >1 dictionary word.
        val ranges = WordTokenizer.tokenize("สวัสดีตอนเช้า")
        assertTrue("expected multiple Thai words", ranges.size >= 2)
        ranges.forEach { assertTrue(it.first in 0..it.last && it.last < "สวัสดีตอนเช้า".length) }
    }
    @Test fun ranges_are_contiguous_and_cover_input() {
        val input = "สวัสดีตอนเช้า"
        val ranges = WordTokenizer.tokenize(input)
        assertEquals(0, ranges.first().first)
        assertEquals(input.length - 1, ranges.last().last)
    }
    @Test fun blank_yields_empty() { assertTrue(WordTokenizer.tokenize("   ").isEmpty()) }
    @Test fun ascii_word_is_single_range() {
        val ranges = WordTokenizer.tokenize("Kanom")
        assertEquals(1, ranges.size)
    }
}
```

- [ ] **Step 3: Run — verify it fails.** `./gradlew :text:pipeline:test --tests "*WordTokenizerTest"` → FAIL.

- [ ] **Step 4: Implement `WordTokenizer`** — `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/WordTokenizer.kt`:

```kotlin
package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

/** ICU dictionary word boundaries (Thai locale). Returns end-exclusive word [IntRange]s over [input]. */
object WordTokenizer {
    fun tokenize(input: String): List<IntRange> {
        if (input.isBlank()) return emptyList()
        val bi = BreakIterator.getWordInstance(ULocale.forLanguageTag("th"))
        bi.setText(input)
        val out = mutableListOf<IntRange>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            if (input.substring(start, end).isNotBlank()) out += start until end
            start = end
            end = bi.next()
        }
        return out
    }
}
```

- [ ] **Step 5: Run — verify it passes.** `./gradlew :text:pipeline:test --tests "*WordTokenizerTest"` → PASS.

- [ ] **Step 6: Write the failing `ThaiZwspInjector` test** — `app/src/test/kotlin/com/nextz/kanomtts/reader/engine/ThaiZwspInjectorTest.kt`:

```kotlin
package com.nextz.kanomtts.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThaiZwspInjectorTest {
    private val ZWSP = '​'
    @Test fun inserts_zwsp_inside_thai_text_node() {
        val out = ThaiZwspInjector.inject("<p>สวัสดีตอนเช้า</p>")
        assertTrue("expected ZWSP inside Thai run", out.contains(ZWSP))
    }
    @Test fun leaves_ascii_untouched() {
        val out = ThaiZwspInjector.inject("<p>Hello Kanom</p>")
        assertFalse(out.contains(ZWSP))
    }
    @Test fun does_not_touch_tags_or_attributes() {
        val out = ThaiZwspInjector.inject("<a href=\"ก.html\">ลิงก์</a>")
        assertFalse("attribute value must not gain ZWSP", out.substringBefore('>').contains(ZWSP))
    }
    @Test fun idempotent() {
        val once = ThaiZwspInjector.inject("<p>สวัสดีตอนเช้า</p>")
        assertEquals(once, ThaiZwspInjector.inject(once))
    }
}
```

- [ ] **Step 7: Run — verify it fails.** `./gradlew :app:testDebugUnitTest --tests "*ThaiZwspInjectorTest"` → FAIL.

- [ ] **Step 8: Implement `ThaiZwspInjector`** — `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ThaiZwspInjector.kt`:

```kotlin
package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.text.pipeline.WordTokenizer
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Inserts U+200B at ICU Thai word boundaries inside text nodes only (skip tags/attrs/script/style),
 * so the WebView can line-break Thai (Blink does not dictionary-break Thai from lang alone — recon §6).
 * Idempotent: a boundary already followed by U+200B is not doubled.
 */
object ThaiZwspInjector {
    private const val ZWSP = "​"

    fun inject(html: String): String {
        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: org.jsoup.nodes.Node, depth: Int) {
                if (node is TextNode) {
                    val parent = (node.parent() as? org.jsoup.nodes.Element)?.tagName()?.lowercase()
                    if (parent == "script" || parent == "style") return
                    val text = node.wholeText
                    if (text.isBlank()) return
                    node.text(insert(text))
                }
            }
            override fun tail(node: org.jsoup.nodes.Node, depth: Int) {}
        }, doc)
        return doc.outerHtml()
    }

    private fun insert(text: String): String {
        val ranges = WordTokenizer.tokenize(text)
        if (ranges.size < 2) return text
        val sb = StringBuilder(text.length + ranges.size)
        var i = 0
        // insert ZWSP at each interior word boundary (range end), idempotently
        val boundaries = ranges.dropLast(1).map { it.last + 1 }.toSet()
        while (i < text.length) {
            sb.append(text[i])
            if ((i + 1) in boundaries && (i + 1 >= text.length || text[i + 1] != '​') && text[i] != '​') {
                sb.append(ZWSP)
            }
            i++
        }
        return sb.toString()
    }
}
```
> The exact `insert` boundary logic is the load-bearing detail; the four tests pin behaviour (Thai gets ZWSP, ASCII/tags/attrs untouched, idempotent). Tune the implementation until all four pass; jsoup `xmlParser()` preserves XHTML well-formedness (recon §6 caveat).

- [ ] **Step 9: Run — verify it passes.** `./gradlew :app:testDebugUnitTest --tests "*ThaiZwspInjectorTest"` → PASS (4).

- [ ] **Step 10: Wire the transform into a display publication.** Add to `PublicationProvider`:
```kotlin
suspend fun openForDisplay(uri: String, thaiWordBreak: Boolean): Publication? {
    val pub = open(uri) ?: return null
    if (!thaiWordBreak) return pub
    val transformer: (org.readium.r2.shared.util.Url, org.readium.r2.shared.util.resource.Resource) -> org.readium.r2.shared.util.resource.Resource = { url, res ->
        val s = url.toString()
        if (s.endsWith(".xhtml") || s.endsWith(".html"))
            res.map { bytes -> org.readium.r2.shared.util.Try.success(ThaiZwspInjector.inject(bytes.decodeToString()).encodeToByteArray()) }
        else res
    }
    @OptIn(org.readium.r2.shared.InternalReadiumApi::class)
    return org.readium.r2.shared.publication.Publication(
        manifest = pub.manifest,
        container = org.readium.r2.shared.util.resource.TransformingContainer(pub.container, transformer),
    )
}
```
> **Unresolved (recon §8 item 5):** verify whether the original `ServicesBuilder` must be re-supplied (cover/content services). If the rebuilt `Publication` loses services the reader needs, use the parse-time container-wrap fallback. The `ReaderBookSource.open` for the reader switches to `openForDisplay(uri, prefs.thaiWordBreak)`; the **content/TTS** path (Plan 07) keeps the un-transformed `open(uri)`.

- [ ] **Step 11: Add escape-hatch prefs + extend the mapper.** Add to `KanomPreferences`: `usePublisherStyles: Boolean = false`, `forceFont: Boolean = true`, `stripColors: Boolean = false`, `thaiWordBreak: Boolean = true`. Add the four `booleanPreferencesKey`s + getters/setters in `PreferencesRepository` (DataStore — no migration). Extend `EpubPreferencesMapper.map(prefs)` to read these from `prefs` (effective `publisherStyles = prefs.usePublisherStyles && !prefs.stripColors`; `forceFont`/`stripColors` semantics). Add mapper tests for the truth table + a `PreferencesRepositoryTest` round-trip for the new keys (mirror existing tests). Add toggles to `ReaderSettingsSheet`.

- [ ] **Step 12: Run all unit tests + build.** `./gradlew :text:pipeline:test :app:testDebugUnitTest :app:assembleDebug` → all green.

- [ ] **Step 13: Device-validate Thai breaking (recon §8 item 6).** A Thai EPUB wraps Thai lines at word boundaries with `thaiWordBreak` on; off → long unbroken Thai lines. Escape hatches visibly change rendering.

- [ ] **Step 14: Commit.** `git commit -am "feat(reader): Thai word-break ZWSP injection + display escape hatches"`

---

## Task 7: Bookmarks — list + in-page Decoration outlines

**Files:**
- Create: `data/repository/BookmarkStore.kt`, `reader/ReaderBookmarkFactory.kt`, `reader/ReaderBookmarksSheet.kt`, `reader/decoration/BookmarkDecorations.kt`
- Modify: `data/repository/BookmarkRepository.kt`, `data/di/RepositoryBindings.kt`, `reader/ReaderUiState.kt`, `reader/ReaderViewModel.kt`, `reader/ReaderScreen.kt`
- Test: `reader/ReaderBookmarkFactoryTest.kt`

**Interfaces:**
- Produces: `BookmarkStore { observe(bookId); add(BookmarkEntity); remove(id) }`; `ReaderBookmarkFactory.build(bookId, locator, now): BookmarkEntity`; `Ready.bookmarks: List<BookmarkEntity>`; `BookmarkDecorations.toDecorations(List<BookmarkEntity>): List<Decoration>`.

- [ ] **Step 1: Bookmark seam.** Create `BookmarkStore` interface (observe/add/remove); make `BookmarkRepository` implement it (`override`); bind in `RepositoryBindings`.

- [ ] **Step 2: Write the failing factory test** — `app/src/test/kotlin/com/nextz/kanomtts/reader/ReaderBookmarkFactoryTest.kt`:

```kotlin
package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReaderBookmarkFactoryTest {
    @Test fun `builds bookmark with encoded locator and snippet from highlight`() {
        val loc = Locator(
            href = Url("c.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(highlight = "a spoken sentence"),
        )
        val bm = ReaderBookmarkFactory.build(bookId = "b1", locator = loc, now = 42L)
        assertEquals("b1", bm.bookId)
        assertEquals("a spoken sentence", bm.snippet)
        assertEquals(42L, bm.createdAt)
        assertEquals(loc, com.nextz.kanomtts.reader.engine.LocatorSerializer.decode(bm.locator))
    }
}
```

- [ ] **Step 3: Run — verify it fails.** `./gradlew :app:testDebugUnitTest --tests "*ReaderBookmarkFactoryTest"` → FAIL.

- [ ] **Step 4: Implement the factory** — `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderBookmarkFactory.kt`:

```kotlin
package com.nextz.kanomtts.reader

import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import org.readium.r2.shared.publication.Locator

object ReaderBookmarkFactory {
    fun build(bookId: String, locator: Locator, now: Long): BookmarkEntity = BookmarkEntity(
        bookId = bookId,
        locator = LocatorSerializer.encode(locator),
        snippet = locator.text.highlight?.trim().orEmpty(),
        createdAt = now,
    )
}
```

- [ ] **Step 5: Run — verify it passes.** `./gradlew :app:testDebugUnitTest --tests "*ReaderBookmarkFactoryTest"` → PASS.

- [ ] **Step 6: Implement `BookmarkDecorations`** — maps each `BookmarkEntity` to a `Decoration` (id `"bm-${id}"`, locator via `LocatorSerializer.decode`, style `Decoration.Style.Highlight(tint = roseArgb, isActive = false)` — or a custom rose-outline template registered in the fragment `Configuration.decorationTemplates`). Use `KanomColors.highlight.toArgb()` for the rose tint.

- [ ] **Step 7: Wire bookmarks into the VM + screen.** Add `bookmarks: List<BookmarkEntity>` to `Ready`; collect `BookmarkStore.observe(bookId)`. VM `addBookmarkAtCurrent(locator)` → `BookmarkStore.add(ReaderBookmarkFactory.build(bookId, locator, now()))`; `removeBookmark(id)`. Chrome add-bookmark action uses `navigator.currentLocator.value`. `ReaderBookmarksSheet` lists snippet + location, tap → `navigator.go(decode(locator))`, swipe → remove. After each bookmarks change and on first page-load, push `BookmarkDecorations.toDecorations(bookmarks)` via `navigator.applyDecorations(decorations, group = "bookmarks")` from `lifecycleScope` (recon §3 group-replace).

- [ ] **Step 8: Build + device-validate.** Add bookmark at a position → appears in the sheet, an in-page rose outline shows; tap navigates; swipe deletes (outline clears). `./gradlew :app:assembleDebug` → SUCCESS.

- [ ] **Step 9: Commit.** `git commit -am "feat(reader): bookmarks list + in-page Decoration outlines"`

---

## Task 8: Sentence-highlight de-risk harness (debug-only)

**Files:** Create `reader/decoration/SentenceHighlightProbe.kt`; Modify `reader/ReaderScreen.kt` (debug-only affordance), `reader/ReaderViewModel.kt`.

- [ ] **Step 1:** Implement `SentenceHighlightProbe.run(publication, navigator)` (`BuildConfig.DEBUG` guarded): pull `publication.content()` elements (`@OptIn(ExperimentalReadiumApi::class)`), filter `Content.TextElement`, for each run `SentenceTokenizer.tokenize(el.text)` → build a `Decoration` per sentence via `el.locator.copy(text = el.locator.text.substring(range))` + `Decoration.Style.Highlight(tint = roseArgb, isActive = true)`, and `navigator.applyDecorations(all, group = "probe")` from `lifecycleScope`. (Reuses the exact machinery Plan 07's speech highlight will use — recon §5.)
- [ ] **Step 2:** Add a debug-only chrome action (visible only when `BuildConfig.DEBUG`) that triggers the probe for the current resource and a second action to clear group `"probe"` (`applyDecorations(emptyList(), "probe")`).
- [ ] **Step 3: Device-validate (recon §8 item 4, the spec's #1 risk).** On 2–3 real Thai EPUBs, every sentence shows a rose pill landing on correct Thai sentence boundaries; confirm timing of first `applyDecorations` (apply after first page-loaded callback). Note any boundary issues for the `:text:pipeline` backlog.
- [ ] **Step 4:** Build (`./gradlew :app:assembleDebug`) → SUCCESS. Commit: `git commit -am "feat(reader): debug-only sentence-highlight de-risk harness"`

---

## Task 9: Library debt — DETAIL view cleanup

**Files:** Modify `feature/library/LibraryUiState.kt` (or wherever `LibraryViewMode` lives) + `feature/library/LibraryComponents.kt` (the view-mode toggle).

- [ ] **Step 1:** Drop `DETAIL` from the view-mode toggle (it renders identically to `LIST` and showed a confusing Settings icon). Restrict the toggle to `GRID`/`LIST` with proper `material-icons-extended` icons (`GridView` / `ViewList`). Keep the `LibraryViewMode.DETAIL` enum value if other code references it, but remove it from the toggle's iterated options. Update `LibraryViewModelTest`'s `setViewMode` test if it asserted DETAIL in the toggle (it asserts GRID→LIST — unaffected).
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests "*LibraryViewModelTest"` → PASS (existing tests stay green).
- [ ] **Step 3:** Build + glance on device (toggle shows two clear icons). Commit: `git commit -am "fix(library): drop confusing DETAIL toggle; proper grid/list icons"`

---

## Task 10: Whole-branch review + on-device validation

- [ ] **Step 1:** Run the full suite: `./gradlew :text:pipeline:test :app:testDebugUnitTest` → all green. Build: `./gradlew :app:assembleDebug` → SUCCESS.
- [ ] **Step 2:** Final whole-branch review (opus) per the SDD cadence — focus: Compose↔FragmentManager lifecycle correctness, the `@InternalReadiumApi` publication rebuild (services preserved?), decoration group hygiene, opt-in annotations, no rose misuse, two-publication (display vs content) separation.
- [ ] **Step 3: On-device validation matrix** (spec §11; the first runnable reader). On a Pixel + a Samsung device, 2–3 real Thai/mixed EPUBs:
  - Render correctness, chapter navigation, immersive chrome toggle.
  - **Exact resume** across close/reopen.
  - Live display settings: 4 themes incl. true-black, font family/size/line-height/margins, escape hatches, Thai word-break on/off.
  - Bookmark add / navigate / in-page outline / delete.
  - Sentence-highlight de-risk harness (boundaries + rendering).
  - **Carried from Plan 05:** real Readium cover/metadata extraction, SAF picker import, OS "open-with".
- [ ] **Step 4:** Resolve recon §8 unresolved items confirmed during the build (annotations, font DSL, `ReadError`/`AttributesHolder` packages, container-id stability, services-on-rebuild). Record outcomes in the SDD ledger.
- [ ] **Step 5:** Hand off to `superpowers:finishing-a-development-branch`.
- [ ] **Note (deferred to Plan 07's device phase):** `onRangeStart` usability, speech-driven highlight sync, per-script voice routing, Samsung `LANG_MISSING_DATA` install prompt — all need a running `TtsNavigator`.

---

## Self-Review

**Spec coverage:** §1 scope → Tasks 1–9. §2 engine layer → Task 1. §3 hosting → Task 2. §4 VM/resume → Tasks 2–3. §5 chrome/sheets → Tasks 4,5,7. §6 full display incl. Thai word-break → Tasks 5–6. §7 Decoration (outlines + de-risk harness) → Tasks 7–8. §8 library debt → Tasks 5 (icons),9 (DETAIL). §9 data/serialization → Task 1 (LocatorSerializer), Tasks 3,7 (no Room migration; new DataStore keys Task 6). §10/§11 testing + device split → Task 10. §13 risks → device-validation gates in Tasks 2,3,6,8,10. All spec sections covered.

**Placeholder scan:** no "TBD/TODO"; the "device-validate" and "unresolved" steps are explicit gates with concrete checks, not deferred work. Where Readium rendering can't be unit-tested (no fixture/mocking lib), that's stated and a device step substitutes — matching the existing extractor's test reality. The two flagged *implementer choices* (assisted-VM vs SavedStateHandle in Task 2; debounce extraction in Task 3) each give a concrete default + fallback, not an open question.

**Type consistency:** `ReaderUiState.Ready` grows additively (Task 2 base → +`initialLocator` T3 → +`toc` T4 → +`bookmarks` T7). `LocatorSerializer.encode/decode`, `EpubPreferencesMapper.map`, `WordTokenizer.tokenize`, `ThaiZwspInjector.inject`, `ReaderBookmarkFactory.build`, seam methods (`BookReader.get`, `ProgressReader.get`, `ProgressWriter.save`, `BookmarkStore.*`, `ReaderBookSource.*`) are referenced with consistent names/signatures across tasks and against the recon reference. `EpubPreferences`/`Decoration`/`FontFamily` field names match recon §2–§5.
