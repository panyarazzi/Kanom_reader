# Plan 06 — Reader (Readium) · Design Spec

**Date:** 2026-06-23
**Status:** Approved (brainstorm complete) — pending spec review
**Package:** `com.nextz.kanomtts` · minSdk 26 · targetSdk/compileSdk 36 · AGP 9.2.1 · Readium 3.3.0
**Predecessors:** Plans 01–05 (DONE & merged). Fills the `ReaderScreen(bookId)` placeholder on the existing `reader/{bookId}` Compose route.

---

## 1. Scope

### In scope
- **Render** reflowable EPUBs with Readium's `EpubNavigatorFragment`, hosted **inside the existing Compose `reader/{bookId}` route** (single-activity, Compose↔Fragment interop).
- **Exact resume** — restore the saved reading `Locator` on open; persist it (debounced) as the user reads.
- **Full display settings** — Light/Sepia/Dark/True-black themes; font family (Sarabun/Serif/System) + size + line-height + margins; CSS-override escape hatches (disable publisher styles / force font / strip colors); **Thai word-break injection** (U+200B at ICU Thai word boundaries).
- **Bookmarks** — add at current position; bottom-sheet list (snippet + location) with tap-to-navigate and swipe-to-delete; **in-page Decoration outlines** on bookmarked text.
- **Sentence-highlight de-risk harness** (debug builds only) — render rose-pill `Decoration`s on every `SentenceTokenizer` boundary of the current resource, over real Thai content. Front-loads the spec's #1 risk and pre-builds the exact Decoration machinery Plan 07's karaoke highlight reuses.
- **Carried reader debt** (from Plan 05 final review): a shared `withPublication(uri){}` open/close helper reused by the metadata extractor and the reader; hoist `DefaultHttpClient`/`AssetRetriever`/`PublicationOpener` to Hilt singletons; resolve the Library DETAIL-view icon/fate; add `material-icons-extended`.

### Out of scope (later plans)
- TTS playback, the **speech-driven** karaoke highlight, per-script voice routing, `onRangeStart` timing, MediaSession/foreground service, mini-player (Plans 07–08).
- Word-level highlight (Phase 2).
- Audio/sleep-timer/now-playing surfaces (Plan 08).

> The de-risk harness in §8 renders highlights **statically** (no speech). It validates Decoration rendering + Thai sentence boundaries only; it does not exercise any TTS engine.

---

## 2. Architecture overview

New code lives under `com.nextz.kanomtts.reader` (a package in `:app`, per the locked module-collapse decision), with a small shared engine sub-package reused by the existing library feature:

```
com.nextz.kanomtts.reader
  engine/                 Readium open/close plumbing (shared with feature.library)
    ReadiumModule.kt        Hilt @Module — singleton HttpClient/AssetRetriever/PublicationOpener
    PublicationProvider.kt  open(uri): Try<Publication> + withPublication(uri){} helper
    LocatorSerializer.kt    Locator <-> JSON string (for ProgressEntity/BookmarkEntity)
    EpubPreferencesMapper.kt  KanomPreferences -> EpubPreferences (pure)
    ThaiWordBreakTransformer.kt  resource HTML transformer (U+200B injection)
  ReaderViewModel.kt      state, resume, debounced save, bookmarks, prefs
  ReaderUiState.kt        Loading / Error / Ready(...)
  ReaderScreen.kt         (replaces ui/screens/ReaderScreen.kt placeholder)
  ReaderChrome.kt         top bar / bottom progress / tap-to-toggle immersive
  ReaderTocSheet.kt       chapter list
  ReaderBookmarksSheet.kt bookmark list
  ReaderSettingsSheet.kt  display-settings bottom sheet
  decoration/
    BookmarkDecorations.kt   bookmark -> Decoration
    SentenceHighlightProbe.kt  debug-only de-risk harness

text/pipeline (existing pure-JVM module) — ADD:
    WordTokenizer.kt        ICU BreakIterator (word, th) -> word IntRanges
```

`feature.library.ReadiumMetadataExtractor` is **refactored** to consume `engine.PublicationProvider` (removing its per-call `DefaultHttpClient`/`AssetRetriever` construction).

---

## 3. Readium engine layer (resolves carried debt)

### 3.1 Singletons (`ReadiumModule`)
One instance each, `@InstallIn(SingletonComponent::class)`:
- `DefaultHttpClient`
- `AssetRetriever(contentResolver, httpClient)`
- `PublicationOpener(DefaultPublicationParser(context, httpClient, assetRetriever, pdfFactory = null))`

### 3.2 `PublicationProvider`
```kotlin
suspend fun open(uri: String): Try<Publication, OpenError>     // retrieve + open, caller owns close
suspend inline fun <T> withPublication(uri: String, block: (Publication) -> T): T?  // open/use/close
```
Encapsulates the exact sequence proven in the spike: `Uri.parse(uri).toAbsoluteUrl()` → `assetRetriever.retrieve(url)` → `opener.open(asset, allowUserInteraction = false)` → guaranteed `publication.close()` / `asset.close()` on every exit path. The metadata extractor uses `withPublication`; the reader uses `open` (it holds the publication for the screen's lifetime and closes it in `ReaderViewModel.onCleared`).

### 3.3 `LocatorSerializer`
`fun encode(locator: Locator): String = locator.toJSON().toString()` and `fun decode(json: String): Locator? = Locator.fromJSON(JSONObject(json))`. Used for `ProgressEntity.locator` and `BookmarkEntity.locator`. (`ttsLocator` is written by Plan 07; Plan 06 leaves it untouched.) Round-trip unit-tested.

---

## 4. Hosting the Fragment (Compose↔Fragment interop)

- **`MainActivity: ComponentActivity → FragmentActivity`.** `setContent{}` still applies (it's a `ComponentActivity` extension). `supportFragmentManager` now exists for fragment hosting. The window theme (`Theme.Material3.DayNight.NoActionBar`, from `com.google.android.material`) and the retained `appcompat`/`material` deps remain valid — no theme change. The existing `ImportViewModel by viewModels()` + intent handling are preserved.
- **`ReaderScreen`** renders the EPUB via `AndroidView` wrapping a `FragmentContainerView` with a **stable unique id**. On first composition (Ready state) it commits the `EpubNavigatorFragment` to `supportFragmentManager` once (guarded against recomposition); the FragmentManager retains it across recomposition and configuration change. The fragment is built from `EpubNavigatorFactory(publication).createFragmentFactory(initialLocator, listener, config)` — **exact factory/listener/config API to be confirmed during planning** (the spike probed open/content/TTS-navigator/tokenizer, not the render fragment).
- Compose chrome (§6) draws over the `AndroidView` in the same `Box`.

---

## 5. `ReaderViewModel` + `ReaderUiState`

`hiltViewModel`, `bookId` from `SavedStateHandle`.

**Responsibilities**
1. Load `Book` via `BookReader.get(bookId)`.
2. `engine.PublicationProvider.open(book.uri)` → hold the `Publication` for the screen lifetime; `close()` in `onCleared`.
3. Read resume `Locator` via `ProgressReader.get(bookId)` → `LocatorSerializer.decode`.
4. Build the navigator fragment factory + initial `EpubPreferences` (from current `KanomPreferences`).
5. Observe `BookmarkRepository.observe(bookId)` and `PreferencesRepository.preferences`.
6. **Debounced** progress persistence on `navigator.currentLocator` changes (`ProgressReader.save(locator, percent)`), and update `Book.lastOpenedAt`.
7. Bookmark add (from current `Locator` + snippet) / remove.

**`ReaderUiState`**
```kotlin
sealed interface ReaderUiState {
  data object Loading
  data class Error(val message: String)
  data class Ready(
    val publication: Publication,
    val fragmentFactory: FragmentFactory,
    val initialLocator: Locator?,
    val toc: List<Link>,
    val prefs: KanomPreferences,
    val bookmarks: List<Bookmark>,
    val chromeVisible: Boolean,
    val percent: Float,
  )
}
```

`EpubPreferencesMapper.map(KanomPreferences): EpubPreferences` is a **pure function** (TDD'd separately from the Android-bound parts).

---

## 6. Display settings — Full

Applied live by submitting an `EpubPreferences` to the navigator's preferences pipeline (`EpubPreferencesEditor`/`submitPreferences` — exact API confirmed in planning).

| Kanom setting | Mechanism |
|---|---|
| Theme: Light / Sepia / Dark | Readium `Theme.LIGHT/SEPIA/DARK` |
| Theme: **True-black** | `Theme.DARK` + CSS background override to `#000000` (Readium's `Theme` enum has no true-black) |
| Font family: Sarabun / Serif / System | `EpubPreferences.fontFamily`; Sarabun + Serif registered via `fontFamilyDeclarations` so they're selectable |
| Font size / line-height / margins | `fontSize` / `lineHeight` / `pageMargins` |
| **Disable publisher styles** | `publisherStyles = false` |
| **Force font** | `fontFamily` + `publisherStyles = false` |
| **Strip colors** | readium CSS color override (textColor/backgroundColor user properties) |
| **Thai word-break injection** | resource HTML transformer (§6.1), toggle-able |

### 6.1 Thai word-break injection
Thai has no inter-word spaces, so WebView line-breaking can be poor. Mechanism (guaranteed path): a **resource-level HTML transformer** that, for Thai text nodes, inserts `U+200B` (zero-width space) at ICU Thai **word** boundaries, letting the WebView break lines naturally. Requires a new pure-JVM **`WordTokenizer`** in `:text:pipeline` (`BreakIterator.getWordInstance(th)` → word `IntRange`s), TDD'd alongside the existing `SentenceTokenizer`.

> **Planning spike (cheap-path first):** try CSS-only (`lang="th"` + `word-break`/`line-break`) before the transformer. If the bundled WebView under-breaks Thai, fall back to U+200B injection. The spec commits to U+200B injection as the guaranteed mechanism; CSS-only is an optimization to confirm or reject during planning.

---

## 7. Reader UI & chrome

- **Immersive by default**; **tap the center** toggles chrome.
- **Top app bar:** back · book title · ToC · add-bookmark · display-settings.
- **Bottom bar:** reading progress (% + current chapter label).
- **ToC sheet** (`ReaderTocSheet`): chapters from `publication.tableOfContents()` → `navigator.go(locator)`.
- **Bookmarks sheet** (`ReaderBookmarksSheet`): snippet + location, tap-to-navigate, swipe-to-delete.
- **Display-settings sheet** (`ReaderSettingsSheet`): theme chips, font family, size/line-height/margin sliders, escape-hatch toggles, Thai word-break toggle — all driven by `PreferencesRepository`.
- Styling uses the Anchan tokens (`KanomTheme`, `ReaderTheme.surface()/onSurface()`, Sarabun body, rose reserved for highlight/bookmark).

---

## 8. Decoration — outlines + de-risk harness

- **Bookmark outlines** (`BookmarkDecorations`): map each bookmark `Locator` → a Readium `Decoration` with a rose outline style; apply via the navigator's `DecorableNavigator`; refresh on navigation. **Decoration API confirmed in planning.**
- **Sentence-highlight de-risk** (`SentenceHighlightProbe`, **`BuildConfig.DEBUG` only**): pull current resource text via the `Content` service → `SentenceTokenizer.tokenize` → refine each text element's `Locator` with the sentence snippet (`Locator.Text(highlight=…)`) → apply as rose-pill `Decoration`s. Lets the user eyeball, on real Thai/mixed EPUBs, that (a) Decoration rendering works and (b) Thai sentence boundaries are correct — the spec's biggest risk, validated the moment the reader runs. This is the same machinery Plan 07's speech-driven highlight will reuse (minus utterance timing).

---

## 9. Library debt

- Add `androidx.compose.material:material-icons-extended` (R8 tree-shakes unused icons in release; size cost is debug-only). Reader chrome gets proper bookmark / text-size / table-of-contents / tune icons.
- **Library DETAIL view:** since `DETAIL == LIST` today and the toggle showed a confusing Settings icon, either give DETAIL a real distinct icon or **drop DETAIL from the view-mode toggle** (default recommendation: drop it; revisit a richer detail view later). Decided during planning.

---

## 10. Data & serialization

- **`ProgressEntity`** (exists): write `locator` (rendered position JSON) + `percent` + `updatedAt`. `ttsLocator` untouched (Plan 07).
- **`BookmarkEntity`** (exists): write `locator` (JSON) + `snippet` + `createdAt`.
- **`KanomPreferences`** (exists): all display fields already present (`readerTheme`, `readingFont`, `fontSizeSp`, `lineHeight`, `marginDp`); `skipUrls`/`highlightOn`/`autoScroll` belong to TTS (Plan 07) and are not consumed here. **No Room schema change** in Plan 06.
- New escape-hatch toggles (disable-publisher-styles / force-font / strip-colors / thai-word-break) are **new DataStore preference keys** added to `KanomPreferences` + `PreferencesRepository` (DataStore is schemaless — no migration). If we prefer to avoid touching `KanomPreferences`, planning may scope these as in-session reader state; default is to persist them as DataStore keys.

---

## 11. Testing strategy & device-checklist split

### JVM / Robolectric (TDD)
- `LocatorSerializer` round-trip (encode→decode equality on representative Locators).
- `EpubPreferencesMapper` (each `KanomPreferences` field → expected `EpubPreferences`; true-black special case).
- `WordTokenizer` (Thai word `IntRange`s; mixed Thai+English; empty/whitespace).
- `ThaiWordBreakTransformer` (U+200B inserted in Thai runs at word boundaries; non-Thai/markup untouched; idempotent).
- `ReaderViewModel` logic with fakes (fake `PublicationProvider`, fake repositories): resume-locator load, debounced save coalescing, bookmark add/remove, prefs→state flow. Robolectric where Android types leak.

### Manual / device (end of plan — *first runnable reader*)
- Render correctness on 2–3 real Thai/mixed EPUBs; chapter navigation; immersive toggle.
- **Exact resume** (close mid-chapter, reopen, land on the same position).
- Live display settings (themes incl. true-black, font family/size/line-height/margins, escape hatches, Thai word-break).
- Bookmark add / navigate / in-page outline.
- Sentence-highlight de-risk harness on real Thai content.
- **Carried from Plan 05** (need the device): real Readium cover/metadata extraction, SAF picker import, OS "open-with".

### Deferred to Plan 07's device phase (need a running `TtsNavigator`)
`onRangeStart` usability on the Google engine for Thai · **speech-driven** highlight sync · per-script voice routing (TH→th-TH / EN→en-US / JA / ZH) · Samsung `LANG_MISSING_DATA` install-voice prompt.

---

## 12. Build sequence (task seams for writing-plans)

1. **Engine layer** — `ReadiumModule` singletons + `PublicationProvider` (`open`/`withPublication`) + refactor `ReadiumMetadataExtractor` onto it; `LocatorSerializer` (+ tests).
2. **First runnable bare render** — `MainActivity → FragmentActivity`; `ReaderScreen` `AndroidView`+`FragmentContainerView` hosting `EpubNavigatorFragment`; `ReaderViewModel` opens the publication and shows the book. **(Harness milestone — device-eyeballing starts here.)**
3. **Resume** — load initial `Locator`; debounced progress save + `lastOpenedAt`.
4. **ToC + navigation.**
5. **Display settings core** — `EpubPreferencesMapper` + settings sheet (themes incl. true-black, font family/size/line-height/margins) applied live.
6. **Display escape hatches + Thai word-break** — `WordTokenizer` + `ThaiWordBreakTransformer` + escape-hatch toggles (after the CSS-vs-injection spike).
7. **Bookmarks** — add/list/navigate/delete + in-page Decoration outlines.
8. **Sentence-highlight de-risk harness** (debug-only).
9. **Library debt** — `material-icons-extended` + DETAIL-view cleanup.
10. **Whole-branch review + on-device validation** (per §11).

---

## 13. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Compose↔FragmentManager lifecycle (double-commit, config-change, container id) | Commit-once guard; rely on FragmentManager retention; isolate in `ReaderScreen`; validate on device early (Task 2). |
| Thai U+200B injection interfering with Readium's resource pipeline / Locators | CSS-first spike before injection; test transformer purity on JVM; verify Locators/bookmarks still resolve on device. |
| True-black / strip-colors via CSS overrides not honored by readium CSS | Confirm the exact readium-CSS user properties in planning; fall back to a custom injected stylesheet. |
| Render/preferences/Decoration APIs not probed by the Plan 02 spike | Planning does focused API recon (context7 `/readium/kotlin-toolkit` + source) before Tasks 2/5/7; treat as the first sub-task of each. |
| Scope (this is the largest plan yet) | The 10-task seam keeps each task bite-sized and independently reviewable; Tasks 1–4 deliver a usable reader before the heavier 5–8. |

---

## 14. Open items (resolve during planning)
- Exact `EpubNavigatorFactory.createFragmentFactory` / `EpubNavigatorFragment.Listener` / `EpubNavigatorFragment.Configuration` signatures (3.3.0).
- Exact `EpubPreferences` / `EpubPreferencesEditor` field names + the live-apply call.
- Exact `DecorableNavigator` / `Decoration` / `Decoration.Style` API + how a char-range maps to a decorable `Locator`.
- `fontFamilyDeclarations` registration mechanism for bundled Sarabun/Serif.
- readium-CSS user properties for strip-colors + true-black background override.
- CSS-only Thai line-break vs U+200B injection (spike) — confirm or reject the cheap path.
- Persist escape-hatch toggles as DataStore keys vs in-session reader state.
- Library DETAIL view: drop from toggle vs distinct icon.
