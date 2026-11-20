# Spike: Readium Kotlin Toolkit build-compatibility + TTS/tokenizer API recon

- **Date:** 2026-06-23
- **Plan:** 02 (Integration Spike) — Task 1
- **Branch:** `spike/readium-integration`
- **Goal:** Does the Readium Kotlin Toolkit resolve and COMPILE against this project's toolchain (AGP 9.2.1, compileSdk 36, minSdk 26, Kotlin 2.1.0)? And what is the concrete API for: open EPUB -> reading-order content -> TTS navigator -> the text-tokenizer SPI a custom Thai sentence tokenizer would implement?

---

## VERDICT: **GO** ✅ (Readium 3.3.0)

> **Correction (2026-06-23, version recheck):** an earlier draft of this doc used 3.1.1 and claimed 3.3.0 was "not resolvable / README-only". That was WRONG — it relied on a STALE `search.maven.org` solr index. The authoritative `repo1.maven.org/.../readium-shared/maven-metadata.xml` lists `<release>3.3.0</release>` / `<latest>3.3.0</latest>` (lastUpdated 20260602). **3.3.0 is published and builds.** This doc now reflects 3.3.0; see the "Version note" and "Version recheck" sections.

Readium **3.3.0** resolves entirely from **Maven Central** (no extra repo) and `./gradlew :app:assembleDebug` produces **BUILD SUCCESSFUL** — Readium resolved, compiled, manifest-merged, desugared, and dexed into the debug APK. The probe (`ReadiumProbe.kt`) references the real open-EPUB, content, TTS-navigator, and tokenizer-SPI types and compiles clean (no warnings). All five 3.3.0 artifacts resolve on `debugRuntimeClasspath`.

The custom Thai tokenizer plugs in **directly and cleanly**: Readium's tokenizer SPI is `typealias TextTokenizer = Tokenizer<String, IntRange>` (single method `fun tokenize(data: String): List<IntRange>`), and the `TtsNavigatorFactory` takes a `tokenizerFactory: (language: Language?) -> TextTokenizer`. Our Plan 01 `SentenceTokenizer.tokenize(String): List<Sentence(text, start, end)>` adapts in one line by mapping each `Sentence` to `start until end`.

There are **toolchain bumps** required (Java 11 -> 17 + core library desugaring), detailed below. These affect the whole `:app` module but are standard for any modern AGP-9 Android app and do not block.

---

## Exact versions / coordinates / repo

| Item | Value |
|---|---|
| Readium version | **3.3.0** (authoritative latest on Maven Central per repo1 maven-metadata.xml, lastUpdated 20260602) |
| Group | `org.readium.kotlin-toolkit` |
| Extra Maven repo needed? | **No** — all artifacts are on `mavenCentral()`, already present in `settings.gradle.kts` |
| Artifacts used | `readium-shared`, `readium-streamer`, `readium-navigator`, `readium-navigator-media-tts`, `readium-navigator-media-common` |

```toml
# gradle/libs.versions.toml
readium = "3.3.0"
readium-shared              = { group = "org.readium.kotlin-toolkit", name = "readium-shared",               version.ref = "readium" }
readium-streamer            = { group = "org.readium.kotlin-toolkit", name = "readium-streamer",             version.ref = "readium" }
readium-navigator           = { group = "org.readium.kotlin-toolkit", name = "readium-navigator",            version.ref = "readium" }
readium-navigator-media-tts    = { group = "org.readium.kotlin-toolkit", name = "readium-navigator-media-tts",    version.ref = "readium" }
readium-navigator-media-common = { group = "org.readium.kotlin-toolkit", name = "readium-navigator-media-common", version.ref = "readium" }
```

### Version note — 3.3.0 IS resolvable (earlier "not on Maven Central" finding was wrong)

The authoritative source for "what is published" is the repository metadata, NOT the search.maven.org solr index:
`https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-shared/maven-metadata.xml` lists `<release>3.3.0</release>`, `<latest>3.3.0</latest>` (lastUpdated 20260602), and 3.1.2 / 3.2.0 / 3.3.0 are all present (same for `readium-navigator-media-tts`). The earlier draft's claim that "only 3.1.1 is resolvable" came from search.maven.org's solr index, which was **stale** — it is a secondary index, not the repo itself, and lagged behind the actual artifacts. **Lesson: verify published versions against `maven-metadata.xml` on repo1.maven.org, not search.maven.org's solr.**

3.3.0 was then **empirically verified**: `readium = "3.3.0"` resolves all five artifacts and `./gradlew :app:assembleDebug` is BUILD SUCCESSFUL on this toolchain. The previously-unverified "3.3.0 needs Kotlin 2.3.20" claim is **REFUTED** — see "Version recheck" for the actual built-in Kotlin compiler version AGP uses.

### Readium 3.3.0 declared requirements (from the README) vs. ours

| Requirement | Readium 3.3.0 | This project | OK? |
|---|---|---|---|
| Android minSdk | 23 | 26 | ✅ (ours higher) |
| Android compileSdk | 36 | 36 | ✅ |
| Kotlin compiler | (README/develop says 2.3.20) | `:app` compiles via AGP 9.2.1 built-in Kotlin = **2.2.10** (catalog `kotlin = 2.1.0` only drives the standalone `kotlin.jvm` plugin on the pure-JVM `:text:*` modules) | ✅ in practice — 2.2.10 reads 3.3.0's metadata; probe compiles clean, no metadata-version error |
| Gradle | 9.1.0+ | 9.4.1 (wrapper) | ✅ |
| Core library desugaring | required | enabled | ✅ Readium uses `java.time` / `java.nio` APIs; desugaring is mandatory (see below). |

---

## Toolchain changes required (project-wide finding)

To compile against Readium, `:app` needed three changes. **Record these — they affect the whole app module.**

1. **Java 11 -> Java 17.** `compileOptions { sourceCompatibility/targetCompatibility = VERSION_17 }`. Readium's AARs are built for Java 17 bytecode; Java 11 will not consume them. (The local JDK is already 17.)

2. **Core library desugaring enabled.** `compileOptions { isCoreLibraryDesugaringEnabled = true }` + `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")`. Readium uses `java.time` / `java.nio` APIs. The build's `l8DexDesugarLibDebug` task ran successfully.

3. **No Kotlin plugin in `:app` — AGP 9 has BUILT-IN Kotlin.** This was the key gotcha.
   - `:app` was a Views/Java-only module (only `com.android.application` applied). To compile the Kotlin probe, the instinct is to add `org.jetbrains.kotlin.android`.
   - **That fails** under AGP 9.2.1 with `Cannot add extension with name 'kotlin', as there is an extension already registered with that name`, because **AGP 9.0+ registers the `kotlin` extension itself and compiles `src/main/kotlin` automatically** (built-in Kotlin, enabled by default).
   - **Fix:** do NOT apply any Kotlin plugin in `:app`. `src/main/kotlin/**` is picked up for free. `android.kotlinOptions {}` is deprecated; Kotlin `jvmTarget` defaults to `compileOptions.targetCompatibility` (17), so no extra block is needed. (If a `compilerOptions` override is ever needed, use the top-level `kotlin { compilerOptions { } }` DSL, not `android.kotlinOptions`.)

Jetpack Compose was **NOT** required for any referenced API and was not enabled.

---

## The concrete API (open EPUB -> content -> TTS navigator -> tokenizer)

All snippets below are the compiling forms from `app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt`. Several APIs are `@ExperimentalReadiumApi` (Content service, TTS factory, tokenizer) — opt in with `@OptIn(ExperimentalReadiumApi::class)`.

### 1. Open an EPUB

```kotlin
import org.readium.r2.shared.util.asset.AssetRetriever          // private ctor; AssetRetriever(...) via companion invoke
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl                          // 3.3.0: File.toUrl(isDirectory: Boolean): AbsoluteUrl
import org.readium.r2.shared.util.getOrElse                      // Try<S,F>.getOrElse { ... }
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

val httpClient = DefaultHttpClient()
val assetRetriever = AssetRetriever(application.contentResolver, httpClient)
val opener = PublicationOpener(
    publicationParser = DefaultPublicationParser(
        context = application,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null,            // PdfDocumentFactory<*>? — null is fine for EPUB-only
    ),
)
val url = File("/path/book.epub").toUrl(isDirectory = false)   // 3.3.0 requires isDirectory (3.1.1 had a no-arg overload)
val asset = assetRetriever.retrieve(url).getOrElse { /* fail */ }
val publication = opener.open(asset, allowUserInteraction = false).getOrElse { /* fail */ }
```

### 2. Reading-order content

`content()` is a `Publication` extension from the `ContentService` (in `org.readium.r2.shared.publication.services.content`). `Content.text()` and `Content.elements()` are **suspend**. Elements arrive in reading order; `Content.TextElement` carries `role` (Heading/Body/Footnote/Quote) and `segments` (language-annotated `Content.TextElement.Segment`s).

```kotlin
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

val content: Content = publication.content() ?: return       // null = content extraction unsupported
val wholeText: String? = content.text()                       // suspend; flattened plain text
val bodyText: List<String> = content.elements()              // suspend
    .filterIsInstance<Content.TextElement>()
    .map { it.text }
```

### 3. TTS navigator

`TtsNavigatorFactory` (package `org.readium.navigator.media.tts`) has a companion `operator fun invoke`. The Android overload returns `AndroidTtsNavigatorFactory?` (a typealias for `TtsNavigatorFactory<AndroidTtsSettings, AndroidTtsPreferences, AndroidTtsPreferencesEditor, AndroidTtsEngine.Error, AndroidTtsEngine.Voice>`); it is **null** when the publication can't be spoken. `createNavigator(listener, initialLocator?, initialPreferences?)` is **suspend** and returns `Try<TtsNavigator, Error>`.

Android overload signature (verified against 3.1.1 source):
```kotlin
operator fun invoke(
    application: Application,
    publication: Publication,
    tokenizerFactory: (language: Language?) -> TextTokenizer = defaultTokenizerFactory,
    metadataProvider: MediaMetadataProvider = defaultMediaMetadataProvider,
    defaults: AndroidTtsDefaults = AndroidTtsDefaults(),
    voiceSelector: (Language?, Set<AndroidTtsEngine.Voice>) -> AndroidTtsEngine.Voice? = defaultVoiceSelector,
): AndroidTtsNavigatorFactory?
```

```kotlin
import org.readium.navigator.media.tts.TtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsDefaults

val factory = TtsNavigatorFactory(
    application = application,
    publication = publication,
    tokenizerFactory = { language -> ThaiSentenceTextTokenizer() },  // <-- our tokenizer plugs in here
    defaults = AndroidTtsDefaults(),
)   // returns AndroidTtsNavigatorFactory? — null if publication not speakable
// val navigator = factory.createNavigator(listener, initialLocator, initialPreferences)  // suspend -> Try<>
```

### 4. The tokenizer SPI + Thai adapter (headline result)

The SPI is tiny and a perfect fit:
```kotlin
// org.readium.r2.shared.util.tokenizer
typealias TextTokenizer = Tokenizer<String, IntRange>           // split a String into char-range tokens
@ExperimentalReadiumApi interface Tokenizer<D, T> { fun tokenize(data: D): List<T> }
enum class TextUnit { Word, Sentence, Paragraph }
// Default impl: DefaultTextContentTokenizer(unit: TextUnit, language: Language?) -> ICU on API 24+, naive below
```

Readium hands the tokenizer the plain text of a content element and expects back the **character ranges** (`IntRange`, 0-based, end-exclusive) of each utterance. Our Plan 01 `SentenceTokenizer` already returns `List<Sentence(text, start, end)>` with absolute offsets into that same string, so the adapter is a one-liner — no reshaping, and the Thai-specific splitting (ฯ + intra-Thai whitespace clause breaks) is preserved:

```kotlin
import com.nextz.kanomtts.text.pipeline.SentenceTokenizer
import org.readium.r2.shared.util.tokenizer.TextTokenizer

@OptIn(ExperimentalReadiumApi::class)
class ThaiSentenceTextTokenizer : TextTokenizer {
    override fun tokenize(data: String): List<IntRange> =
        SentenceTokenizer.tokenize(data).map { it.start until it.end }
}
```

Wire it via `tokenizerFactory = { language -> ThaiSentenceTextTokenizer() }`. (The `language: Language?` arg lets us pick a tokenizer per content language if we ever want Thai-only behavior for `th` and `DefaultTextContentTokenizer(TextUnit.Sentence, language)` otherwise.)

> Caveat worth a Plan-06/07 follow-up: `SentenceTokenizer` uses ICU `BreakIterator` with a Thai locale and runs **per element text**, which is exactly what Readium expects. It operates on the element's plain text only (no cross-element context), which matches Readium's per-element tokenization model — no impedance mismatch found.

---

## Files changed by this spike

- `gradle/libs.versions.toml` — `readium = "3.1.1"`, `desugarJdkLibs = "2.1.5"`, 5 Readium library aliases + `desugar-jdk-libs`.
- `app/build.gradle.kts` — Java 17, core library desugaring, `project(":text:pipeline")` + 5 Readium deps + desugar runtime. (No Kotlin plugin — AGP built-in.)
- `app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt` — compile-only probe (new).
- `build.gradle.kts`, `settings.gradle.kts` — **unchanged** (no extra repo needed; root plugins untouched).

## Build evidence

```
> Task :app:l8DexDesugarLibDebug
> Task :app:compileDebugKotlin
> Task :app:checkDebugDuplicateClasses
> Task :app:mergeExtDexDebug
> Task :app:dexBuilderDebug
> Task :app:packageDebug
> Task :app:assembleDebug
BUILD SUCCESSFUL
```
- `app/build/outputs/apk/debug/app-debug.apk` produced.
- All 5 Readium **3.3.0** artifacts resolved on `debugRuntimeClasspath` from Maven Central; `desugar_jdk_libs:2.1.5` resolved. No compiler warnings.

## Sources consulted

- context7 `/readium/kotlin-toolkit` — TTS guide (tokenizerFactory, AndroidTtsNavigatorFactory), getting-started (AssetRetriever/PublicationOpener), ContentService.
- Readium GitHub (tag `3.1.1`) source, verified via GitHub API/raw:
  - `readium/shared/.../util/tokenizer/TextTokenizer.kt`, `Tokenizer.kt` (SPI)
  - `readium/navigators/media/tts/.../TtsNavigatorFactory.kt`, `TtsAliases.kt`, `android/AndroidTtsDefaults.kt`
  - `readium/streamer/.../PublicationOpener.kt`, `parser/PublicationParser.kt`
  - `readium/shared/.../util/Url.kt`, `util/asset/AssetRetriever.kt`, `util/http/DefaultHttpClient.kt`, `util/Try.kt`, `publication/services/content/Content.kt`, `ContentService.kt`
- **Authoritative published-versions source:** `repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-shared/maven-metadata.xml` (`<release>3.3.0</release>`, lastUpdated 20260602). NOTE: `search.maven.org/solrsearch` was **stale** (showed 3.1.1 as newest) — do not rely on it for "latest published".
- Android Developers: "Migrate to built-in Kotlin" (AGP 9 built-in Kotlin; `kotlinOptions` -> `kotlin.compilerOptions`).

## Version recheck (3.1.1 -> 3.3.0)

- **Chosen version: 3.3.0** — the authoritative latest, and it builds. No step-down to 3.2.0/3.1.2 needed.
- **Resolution:** all five artifacts (`readium-shared`, `-streamer`, `-navigator`, `-navigator-media-tts`, `-navigator-media-common`) resolve at 3.3.0 from `mavenCentral()`. No extra repo.
- **"3.3.0 needs Kotlin 2.3.20" — REFUTED.** AGP 9.2.1's *built-in* Kotlin compiler is **2.2.10** (verified: `kotlin-build-tools-impl-2.2.10.jar` / `kotlin-compiler-embeddable-2.2.10` in the Gradle cache; build log shows "Using Kotlin Gradle Plugin gradle813 variant" and the `built_in_kotlinc` intermediates dir). 2.2.10 reads 3.3.0's `.kotlin_module`/metadata with no version error. (The catalog's `kotlin = "2.1.0"` governs only the standalone `kotlin.jvm` plugin used by the pure-JVM `:text:*` modules; it does NOT drive `:app`, which uses AGP built-in Kotlin.)
- **Toolchain delta vs. 3.1.1: NONE.** Same Java 17 + core library desugaring (`desugar_jdk_libs:2.1.5`) + no Kotlin plugin in `:app`. No different Java/Kotlin/AGP/Gradle setting was required to move from 3.1.1 to 3.3.0.
- **Only API change accommodated:** `File.toUrl()` (no-arg in 3.1.1) became `File.toUrl(isDirectory: Boolean)` in 3.3.0. The probe now passes `isDirectory = false`. All other probed APIs (AssetRetriever, PublicationOpener/DefaultPublicationParser, `publication.content()`, `TtsNavigatorFactory(..., tokenizerFactory, defaults)`, `TextTokenizer` = `Tokenizer<String, IntRange>`) are unchanged between 3.1.1 and 3.3.0.
- **Compile failure observed at first 3.3.0 attempt (then fixed):** `e: ReadiumProbe.kt:69:57 No value passed for parameter 'isDirectory'.` — a genuine source-level API change, NOT a metadata/version-compatibility error.

## Fallback (only relevant if this had been NO-GO)

N/A — verdict is GO. The WebView+JS hand-rolled reader (spec Decision A fallback) is **not** needed. Plans 06/07 should build on Readium 3.3.0 as the reader foundation.
