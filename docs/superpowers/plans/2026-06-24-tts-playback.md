# TTS Playback Implementation Plan — Plan 07

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Read the book aloud with in-paragraph script routing (TH/EN/JA/ZH via the existing `TextPipeline`), sentence-level karaoke highlight + auto-scroll, background playback (media3), a docked mini-player, and a sleep timer.

**Architecture:** A Hilt-`@Singleton` `TtsPlaybackController` is the single source of truth: it streams reading-order content from the **un-transformed** Readium `Publication`, runs each element through `TextPipeline` into language-tagged `SentenceUtterance`s, and speaks each segment via a `TtsProvider` (`SystemTtsProvider` wraps `android.speech.tts.TextToSpeech`). A media3 `SimpleBasePlayer` (`TtsPlayer`) wrapped by a `MediaSessionService` (`TtsPlaybackService`) gives the notification/lock-screen; in-app UI (mini-player, reader highlight) observes the controller directly. Audio focus is self-managed (SimpleBasePlayer does not do it).

**Tech Stack:** Kotlin (AGP-9 built-in 2.2.10), Compose + Hilt, **media3 1.10.1** (`media3-session` + `media3-common`), Readium 3.3.0 (`Content` streaming + Decoration + `SelectableNavigator`), `android.speech.tts`, `:text:pipeline` + `:text:replacement`. Tests: JUnit4, Robolectric `@Config(sdk=[35])`, coroutines-test, Turbine.

**Verified API reference (cite for exact signatures):** `docs/superpowers/spikes/2026-06-24-tts-playback-api-recon.md`. **Spec:** `docs/superpowers/specs/2026-06-24-tts-playback-design.md`.

## Global Constraints

- **Package:** all new TTS code under `com.nextz.kanomtts.playback` (a package in `:app`; the manifest service is `.playback.TtsPlaybackService`, so this package is authoritative — the spec's `tts` name is superseded).
- **media3 1.10.1** via the catalog. **Remove** `readium-navigator-media-tts` + `readium-navigator-media-common` (we use our own engine, not Readium's `TtsNavigator`) — and **delete `app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt`** (the Plan-02 compile-only spike references the removed media-tts `TtsNavigatorFactory` and will otherwise break the build).
- **TTS reads the UN-transformed publication** (`PublicationProvider.open(uri)`, NO Thai ZWSP) — never `openForDisplay()` — so injected U+200B doesn't pollute spoken text. (recon §7)
- **`ProgressEntity.ttsLocator`** is the TTS listening position; the controller writes it without clobbering `locator` (read fresh + copy), mirroring how `ReaderViewModel`'s save preserves `ttsLocator`.
- **Audio focus is self-managed** via `AudioManager.requestAudioFocus(AudioFocusRequest)` + `ACTION_AUDIO_BECOMING_NOISY`; `SimpleBasePlayer.setAudioAttributes()` only reports state. Use the SAME `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)` on the focus request and `TextToSpeech.setAudioAttributes`. (recon §5, §9)
- **All `Player` calls arrive on the player's `Looper`** (main): mutate backing state + `invalidateState()` only there; marshal TTS callbacks (which arrive on a binder thread) back to main. (recon §3b, §6e)
- **`onRangeStart` is best-effort** (Google TTS yes; some OEMs never fire it) — sentence-level highlight does NOT depend on it; the controller moves the highlight when it starts a sentence. (recon §6e)
- **Long-press → Locator** uses Readium text **selection** (`SelectableNavigator.currentSelection()`) + a "Read from here" `selectionActionModeCallback` item — there is NO point→Locator hit-test in 3.3.0. Our `ReaderChrome` `detectTapGestures` has no `onLongPress`, so long-press already passes to the WebView (device-verify). Fallback start = `firstVisibleElementLocator()` ("play from top of screen"). (recon §8)
- Reserve **rose** for the karaoke highlight (Decoration group `"tts"`) + bookmarks; the mini-player/chrome use primary/surface tokens.
- `@OptIn(ExperimentalReadiumApi::class)` for Content/selection; `@OptIn(androidx.media3.common.util.UnstableApi::class)` where media3 requires it (`SimpleBasePlayer`, `MediaSession` builders are `@UnstableApi`).
- **No Room migration** (all fields exist). New TTS prefs, if any, are DataStore keys.

---

## File Structure

**Create (package `com.nextz.kanomtts.playback`)**
- `TtsProvider.kt` — seam: `voices(lang)`, `speak(text, lang, voiceId, speed, pitch)`, `stop()`, `events`.
- `SystemTtsProvider.kt` — `android.speech.tts.TextToSpeech` impl (init gate, voice mapping, suspend bridge).
- `Voice.kt` — domain voice `(id, name, lang, needsNetwork, quality)`.
- `TtsEvent.kt` — `Started/Done/Error/RangeStart`.
- `VoiceRouter.kt` — pure: `Lang` + `VoiceConfig` map → `VoiceChoice`.
- `SentenceUtterance.kt` — `(locator: Locator, segments: List<SpeechSegment>)`.
- `SentenceLocators.kt` — pure: `forSentence(element, start, end): Locator` (reused by the probe).
- `TtsContentSource.kt` — seam: `fun stream(bookId, fromLocator): Flow<SentenceUtterance>` + `DefaultTtsContentSource`.
- `PlaybackState.kt` — `Idle/Loading/Playing/Paused/Error`.
- `TtsPlaybackController.kt` — `@Singleton` engine.
- `SleepTimer.kt` — pure countdown + end-of-chapter + fade policy.
- `AudioFocus.kt` — self-managed focus + becoming-noisy.
- `TtsPlayer.kt` — media3 `SimpleBasePlayer`.
- `TtsPlaybackService.kt` — `MediaSessionService`.
- `di/PlaybackModule.kt` — Hilt bindings (provider, content source).
- `ui/MiniPlayer.kt` — docked Compose mini-player.
- `reader/ReaderDecorationController.kt` — extracted decoration owner (bookmarks + tts + probe).
- Tests: `VoiceRouterTest`, `SentenceLocatorsTest`, `TtsPlaybackControllerTest`, `SleepTimerTest`, `util/FakeTtsProvider.kt`, `util/FakeTtsContentSource.kt`.

**Modify**
- `gradle/libs.versions.toml`, `app/build.gradle.kts` — media3 add, media-tts remove.
- `app/src/main/AndroidManifest.xml` — service + permissions.
- `reader/decoration/SentenceHighlightProbe.kt` — use `SentenceLocators.forSentence`.
- `reader/ReaderViewModel.kt` + `reader/ReaderScreen.kt` — observe the controller (tts highlight + auto-scroll), extract `ReaderDecorationController`, add the play control + long-press "Read from here".
- `reader/ReaderChrome.kt` — a play/pause action.
- `ui/navigation/KanomApp.kt` — dock the mini-player.
- `data/di/RepositoryBindings.kt` or a new module — bindings as needed.
- Delete `spike/ReadiumProbe.kt`.

---

## Task 1: Dependency swap (media3 in, readium-media-tts out)

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`; Delete `app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt`.

- [ ] **Step 1: Catalog.** In `gradle/libs.versions.toml` add under `[versions]`: `media3 = "1.10.1"`. Add under `[libraries]`:
```toml
androidx-media3-common  = { group = "androidx.media3", name = "media3-common",  version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
```
Delete the `readium-navigator-media-tts` and `readium-navigator-media-common` catalog lines.

- [ ] **Step 2: build.gradle.** In `app/build.gradle.kts` dependencies: add `implementation(libs.androidx.media3.session)` + `implementation(libs.androidx.media3.common)`; remove the two `implementation(libs.readium.navigator.media.tts)` / `...media.common` lines.

- [ ] **Step 3: Delete the obsolete spike.** `git rm app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt` (it references the removed `TtsNavigatorFactory`; its findings live in the spike docs). If the `spike` package becomes empty, that's fine.

- [ ] **Step 4: Verify.** Run `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL (media3 resolves; nothing references the removed media-tts artifacts). If a compile error points at another file referencing media-tts, report it.

- [ ] **Step 5: Commit.** `git add -A && git commit -m "build(tts): add media3 1.10.1; drop readium-media-tts + obsolete ReadiumProbe spike"`

---

## Task 2: `TtsProvider` seam + `Voice` + `TtsEvent` + `SystemTtsProvider`

**Files:** Create `playback/Voice.kt`, `TtsEvent.kt`, `TtsProvider.kt`, `SystemTtsProvider.kt`, `di/PlaybackModule.kt`.

**Interfaces:**
- Produces: `interface TtsProvider { suspend fun voices(lang: Lang): List<Voice>; suspend fun speak(text: String, lang: Lang, voiceId: String?, speedRate: Float, pitch: Float); fun stop(); val events: Flow<TtsEvent> }`; `data class Voice(id, name, lang, needsNetwork, quality)`; `sealed interface TtsEvent`.

- [ ] **Step 1: Domain types.** Create `playback/Voice.kt`:
```kotlin
package com.nextz.kanomtts.playback
import com.nextz.kanomtts.text.pipeline.Lang
data class Voice(val id: String, val name: String, val lang: Lang, val needsNetwork: Boolean, val quality: Int)
```
Create `playback/TtsEvent.kt`:
```kotlin
package com.nextz.kanomtts.playback
sealed interface TtsEvent {
    data class Started(val utteranceId: String) : TtsEvent
    data class Done(val utteranceId: String) : TtsEvent
    data class RangeStart(val utteranceId: String, val start: Int, val end: Int) : TtsEvent
    data class Error(val utteranceId: String?, val code: Int) : TtsEvent
    data class MissingVoice(val lang: Lang) : TtsEvent
}
```

- [ ] **Step 2: The seam.** Create `playback/TtsProvider.kt`:
```kotlin
package com.nextz.kanomtts.playback
import com.nextz.kanomtts.text.pipeline.Lang
import kotlinx.coroutines.flow.Flow
interface TtsProvider {
    suspend fun voices(lang: Lang): List<Voice>
    /** Speak one single-language segment; suspends until done; throws on error/cancellation. */
    suspend fun speak(text: String, lang: Lang, voiceId: String?, speedRate: Float, pitch: Float)
    fun stop()
    val events: Flow<TtsEvent>
}
```

- [ ] **Step 3: `SystemTtsProvider`** — create `playback/SystemTtsProvider.kt` per recon §6 (init gate behind a `ready` flag set on `onInit==SUCCESS`; `Lang`→`Locale` map TH→`th-TH`/EN→`en-US`/JA→`ja-JP`/ZH→`zh-CN`; `voices(lang)` = `tts.voices.orEmpty()` filtered by `locale.language` → mapped to domain `Voice`; `speak` resolves `voiceId` to a `tts.voices` `Voice` via `setVoice` (or default for the locale, emitting `MissingVoice` on `isLanguageAvailable<0`), `setSpeechRate`/`setPitch`, then the **suspend bridge** from recon §6g — `suspendCancellableCoroutine` keyed by a generated utteranceId, resumed by the `UtteranceProgressListener.onDone`/`onError`/`onStop`; `setAudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)`; `events: MutableSharedFlow<TtsEvent>` emitting Started/Done/RangeStart/Error). `@Inject constructor(@ApplicationContext context)`; `@Singleton`. Override BOTH `onError(String)` (delegate to `onError(id, ERROR)`) and `onError(String, Int)` (recon §9.5). Copy the exact `TextToSpeech` / `Voice` / `UtteranceProgressListener` signatures + the suspend-bridge from the recon doc §6.

- [ ] **Step 4: Bind.** Create `playback/di/PlaybackModule.kt`: `@Module @InstallIn(SingletonComponent::class) abstract class PlaybackModule { @Binds @Singleton abstract fun ttsProvider(impl: SystemTtsProvider): TtsProvider }`.

- [ ] **Step 5: Verify + commit.** `./gradlew :app:compileDebugKotlin :app:kspDebugKotlin` → SUCCESS (Hilt graph compiles). No unit test here — `SystemTtsProvider` is device-validated; the seam enables the controller tests (Task 5). Note in the report that real speech is device-deferred. Commit: `feat(tts): TtsProvider seam + SystemTtsProvider (TextToSpeech) + Voice/TtsEvent`.

---

## Task 3: `VoiceRouter` (pure, TDD)

**Files:** Create `playback/VoiceRouter.kt`; Test `playback/VoiceRouterTest.kt`.

**Interfaces:** Produces `data class VoiceChoice(voiceId: String?, speed: Float, pitch: Float)`; `object VoiceRouter { fun choose(lang: Lang, configs: Map<String, VoiceConfigEntity>): VoiceChoice }`.

- [ ] **Step 1: Failing test** — `app/src/test/kotlin/com/nextz/kanomtts/playback/VoiceRouterTest.kt`:
```kotlin
package com.nextz.kanomtts.playback
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
import org.junit.Assert.assertEquals
import org.junit.Test
class VoiceRouterTest {
    private fun cfg(lang: String, voiceId: String?, speed: Float, pitch: Float) =
        VoiceConfigEntity(lang = lang, providerId = "system", voiceId = voiceId, speed = speed, pitch = pitch)
    @Test fun `unset lang falls back to defaults`() {
        val c = VoiceRouter.choose(Lang.TH, emptyMap())
        assertEquals(VoiceChoice(null, 1.0f, 1.0f), c)
    }
    @Test fun `configured lang uses its voice and prosody`() {
        val c = VoiceRouter.choose(Lang.EN, mapOf("EN" to cfg("EN", "en-voice-1", 1.5f, 0.9f)))
        assertEquals(VoiceChoice("en-voice-1", 1.5f, 0.9f), c)
    }
    @Test fun `config keyed by Lang name not value`() {
        val c = VoiceRouter.choose(Lang.JA, mapOf("ZH" to cfg("ZH", "zh", 2f, 2f)))
        assertEquals(VoiceChoice(null, 1.0f, 1.0f), c)
    }
}
```
- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests "*VoiceRouterTest"`.
- [ ] **Step 3: Implement** — `playback/VoiceRouter.kt`:
```kotlin
package com.nextz.kanomtts.playback
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
data class VoiceChoice(val voiceId: String?, val speed: Float, val pitch: Float)
object VoiceRouter {
    const val DEFAULT_SPEED = 1.0f
    const val DEFAULT_PITCH = 1.0f
    fun choose(lang: Lang, configs: Map<String, VoiceConfigEntity>): VoiceChoice {
        val c = configs[lang.name]
        return VoiceChoice(c?.voiceId, c?.speed ?: DEFAULT_SPEED, c?.pitch ?: DEFAULT_PITCH)
    }
}
```
- [ ] **Step 4: Run → PASS.** **Step 5: Commit** `feat(tts): VoiceRouter (Lang+VoiceConfig → VoiceChoice)`.

---

## Task 4: `SentenceLocators` (pure, Robolectric TDD) + refactor the probe

**Files:** Create `playback/SentenceLocators.kt`; Test `playback/SentenceLocatorsTest.kt`; Modify `reader/decoration/SentenceHighlightProbe.kt`.

**Interfaces:** Produces `object SentenceLocators { fun forSentence(element: Locator, start: Int, end: Int): Locator }`.

- [ ] **Step 1: Failing test** — `app/src/test/kotlin/com/nextz/kanomtts/playback/SentenceLocatorsTest.kt` (Robolectric — `Locator` needs `org.json`):
```kotlin
package com.nextz.kanomtts.playback
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
class SentenceLocatorsTest {
    @Test fun `forSentence narrows the locator text to the sentence range`() {
        val element = Locator(
            href = Url("ch1.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(highlight = "Hello world. Bye."),
        )
        val s = SentenceLocators.forSentence(element, 0, 12)
        assertEquals("Hello world.", s.text.highlight)
        assertEquals(element.href, s.href)
    }
}
```
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** — `playback/SentenceLocators.kt`:
```kotlin
package com.nextz.kanomtts.playback
import org.readium.r2.shared.publication.Locator
/** The Locator for a sentence spanning [start, end) within [element]'s text (end-exclusive). */
object SentenceLocators {
    fun forSentence(element: Locator, start: Int, end: Int): Locator =
        element.copy(text = element.text.substring(start until end))
}
```
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Refactor the probe** — in `reader/decoration/SentenceHighlightProbe.kt` replace the inline `el.locator.copy(text = el.locator.text.substring(s.start until s.end))` with `com.nextz.kanomtts.playback.SentenceLocators.forSentence(el.locator, s.start, s.end)`. Run `./gradlew :app:testDebugUnitTest` (probe has no direct test; ensure suite green). **Step 6: Commit** `feat(tts): SentenceLocators helper; reuse it in SentenceHighlightProbe`.

---

## Task 5: `TtsPlaybackController` — state machine + speak loop (TDD with fakes)

**Files:** Create `playback/PlaybackState.kt`, `playback/SentenceUtterance.kt`, `playback/TtsContentSource.kt` (interface only), `playback/TtsPlaybackController.kt`; Test `playback/TtsPlaybackControllerTest.kt`, `playback/util/FakeTtsProvider.kt`, `playback/util/FakeTtsContentSource.kt`.

**Interfaces:**
- Produces: `data class SentenceUtterance(val locator: Locator, val segments: List<SpeechSegment>)`; `interface TtsContentSource { fun stream(bookId: String, fromLocator: Locator?): Flow<SentenceUtterance> }`; `sealed interface PlaybackState { Idle; Loading; data class Playing(bookId, locator: Locator); data class Paused(bookId, locator: Locator); data class Error(message) }`; `class TtsPlaybackController @Inject(...) { val state: StateFlow<PlaybackState>; val currentLocator: StateFlow<Locator?>; fun start(bookId, fromLocator); fun play(); fun pause(); fun stop(); fun nextSentence(); fun previousSentence(); fun jumpToSegment(i) }`.
- Consumes: `TtsProvider.speak`, `VoiceRouter.choose`, `VoiceConfigRepository.observeAll`.

- [ ] **Step 1: Types** — `playback/PlaybackState.kt` + `playback/SentenceUtterance.kt` + `playback/TtsContentSource.kt` (interface) per the Interfaces block above (`Locator` import `org.readium.r2.shared.publication.Locator`; `SpeechSegment` import `com.nextz.kanomtts.text.pipeline.SpeechSegment`).

- [ ] **Step 2: Fakes** — `app/src/test/kotlin/com/nextz/kanomtts/playback/util/FakeTtsProvider.kt` (records `speak(text,lang,voiceId,speed,pitch)` calls into a list, completes immediately or after a controllable delay, `events` a MutableSharedFlow) and `FakeTtsContentSource.kt` (`stream` returns `flowOf(fixedUtterances...)`). Also a fake/real `VoiceConfigRepository` — inject the config as a `Map` via a small seam or pass `VoiceConfigRepository` and use a Robolectric DAO; simplest: make the controller take a `suspend () -> Map<String, VoiceConfigEntity>` voice-config supplier (a seam) so tests pass a fixed map.

- [ ] **Step 3: Failing test** — `TtsPlaybackControllerTest` (coroutines-test; Robolectric only if `Locator` construction in utterances requires `org.json` — it does, so `@Config(sdk=[35]) @RunWith(RobolectricTestRunner)`). TDD these behaviors:
  - `start(bookId, null)` then `play()` → the fake provider receives each segment of each utterance **in order** with the routed voice/speed/pitch; `currentLocator` emits each utterance's locator in turn; ends at `Idle`/`Ended`.
  - `pause()` mid-stream → no further `speak` calls; `state` = `Paused`. `play()` resumes from the current sentence (re-speaks it).
  - `nextSentence()` / `previousSentence()` move the cursor (the next `speak` is the next/prev utterance's first segment).
  - voice routing: a TH segment uses the TH `VoiceChoice`, an EN segment the EN one (assert via the recorded `speak` args).
  Write the assertions against the recorded `FakeTtsProvider` calls + a Turbine collection of `currentLocator`.

- [ ] **Step 4: Run → FAIL.**

- [ ] **Step 5: Implement `TtsPlaybackController`** (`@Singleton @Inject constructor(private val provider: TtsProvider, private val voiceConfig: VoiceConfigSupplier, private val contentSource: TtsContentSource, /* + progress writer, book source — wired in Task 6 */)`): a `CoroutineScope` (SupervisorJob + Main), a growing `MutableList<SentenceUtterance>` filled by collecting `contentSource.stream(...)` as it plays, a cursor index, a `playJob`. `play()` launches the speak loop: for the utterance at the cursor, set `state=Playing(locator)` + `currentLocator=locator`, then for each `segment` compute `VoiceRouter.choose(segment.lang, configs)` and `provider.speak(segment.spokenText, segment.lang, choice.voiceId, choice.speed, choice.pitch)` (await), then advance the cursor (pull more from the stream as needed); honor cancellation between segments. `pause()` cancels `playJob` + `state=Paused`. `stop()` cancels + `provider.stop()` + `state=Idle` + clear. `next/previousSentence()` cancel + move cursor + relaunch. Keep it single-threaded on the scope. Use the voice-config supplier to read configs once per `start` (refresh acceptable).

- [ ] **Step 6: Run → PASS.** **Step 7: Commit** `feat(tts): TtsPlaybackController state machine + speak loop (TDD)`.

---

## Task 6: `DefaultTtsContentSource` — stream content → utterances

**Files:** Create `playback/DefaultTtsContentSource.kt`; Modify `playback/di/PlaybackModule.kt` (bind), `playback/TtsPlaybackController.kt` (wire ttsLocator persistence + book primary language).

**Interfaces:** Produces `class DefaultTtsContentSource @Inject(publications: PublicationProvider, books: BookReader, rules: ReplacementRuleRepository, prefs: ReaderPreferences) : TtsContentSource`.

- [ ] **Step 1: Implement the stream.** `DefaultTtsContentSource.stream(bookId, fromLocator): Flow<SentenceUtterance>` = a cold `flow { }` that: `val book = books.get(bookId) ?: return@flow`; `val pub = publications.open(book.uri) ?: return@flow` (UN-transformed); build the pipeline: `val engine = ReplacementEngine(rules.observeEffective(bookId).first())`; `val pipeline = TextPipeline(primary = langOf(book.lang), replacement = { engine.apply(it) }, options = NormalizerOptions(skipUrls = prefs.preferences.first().skipUrls))`; then per recon §7c stream `pub.content(fromLocator)?.iterator()` with a `while (it.hasNext())` loop, and for each `Content.TextElement el`: `pipeline.process(el.text).forEach { s -> emit(SentenceUtterance(SentenceLocators.forSentence(el.locator, s.start, s.end), s.segments)) }`. Add `langOf(bcp47: String?): Lang` (TH default; "en"→EN, "ja"→JA, "zh"→ZH, else TH). `@OptIn(ExperimentalReadiumApi::class)`. Close `pub` in a `finally` / `flow`'s `onCompletion`.
  - *(Inline spike, recon §7b: confirm `Content.Iterator.hasNext()` is suspend + `next()` non-suspend in 3.3.0; use `nextOrNull()` if cleaner.)*

- [ ] **Step 2: ttsLocator persistence + book lang in the controller.** Inject `ProgressReader` + `ProgressWriter` into `TtsPlaybackController`; on each sentence advance (debounced ~1s), save `progressWriter.save( (progressReader.get(bookId) ?: ProgressEntity(bookId, null, null, 0f, now)).copy(ttsLocator = LocatorSerializer.encode(locator), updatedAt = now) )` — preserving `locator` (the reading position). `start(bookId, fromLocator=null)` resolves `fromLocator` from `progressReader.get(bookId)?.ttsLocator` (decoded) so playback resumes where listening stopped.

- [ ] **Step 3: Bind** `@Binds DefaultTtsContentSource → TtsContentSource` in `PlaybackModule`. Provide the `VoiceConfigSupplier` (a `@Provides` lambda reading `VoiceConfigRepository.observeAll().first().associateBy { it.lang }`, or inject `VoiceConfigRepository` directly).

- [ ] **Step 4: Verify.** `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:kspDebugKotlin` → green (controller tests still pass with the fake source; real source is device-validated). No new unit test (streaming needs a real EPUB) — note device-deferred. **Step 5: Commit** `feat(tts): stream un-transformed content → utterances + ttsLocator resume`.

---

## Task 7: `SleepTimer` (pure, virtual-time TDD) + controller integration

**Files:** Create `playback/SleepTimer.kt`; Test `playback/SleepTimerTest.kt`; Modify `TtsPlaybackController.kt`.

- [ ] **Step 1–4 (TDD):** `SleepTimer` exposes `fun start(durationMs: Long?, endOfChapter: Boolean)`, `fun cancel()`, a `StateFlow<Long?>` remaining, and a `fadeFraction: StateFlow<Float>` (1f→0f over the last `FADE_MS` before expiry); fires an `onExpire` callback. TDD with `runTest` virtual time: a 60s timer fires `onExpire` at 60s, `fadeFraction` ramps in the last `FADE_MS`, `cancel()` stops it. End-of-chapter mode arms expiry on the next resource boundary (the controller calls `timer.onChapterBoundary()`).
- [ ] **Step 5: Integrate** — controller exposes `setSleepTimer(durationMin: Int?)` / `endOfChapter`; on expire → fade (scale the segment volume via `provider.speak`'s volume param toward 0 — or just `pause()` after fade) then `pause()`. Default duration = `KanomPreferences.sleepTimerDefaultMin`. **Step 6: Commit** `feat(tts): sleep timer (durations + end-of-chapter + fade)`.

---

## Task 8: media3 `TtsPlayer` + `TtsPlaybackService` + manifest + audio focus

**Files:** Create `playback/TtsPlayer.kt`, `playback/TtsPlaybackService.kt`, `playback/AudioFocus.kt`; Modify `AndroidManifest.xml`, `playback/di/PlaybackModule.kt`.

- [ ] **Step 1: `TtsPlayer : SimpleBasePlayer`** per recon §3f — wraps the `@Singleton TtsPlaybackController`; `getState()` builds `State` from the controller's `state`/playlist (one `MediaItemData` per chapter with `MediaMetadata` title/author/cover from the current book); `handleSetPlayWhenReady`→`controller.play()/pause()`, `handleStop`→`controller.stop()`, `handleSeek`→`controller.jumpToSegment`; collect `controller.state` on the player's Looper and `invalidateState()` on change. `@UnstableApi`. Copy the exact `Player.Commands` + `State.Builder` + `MediaItemData.Builder` (uid spike, recon unresolved #1) from the recon doc §3.
- [ ] **Step 2: `AudioFocus`** per recon §5 — `requestAudioFocus`/`abandon` around play/stop (pause on `LOSS_TRANSIENT`, resume on `GAIN`, stop on `LOSS`) + an `ACTION_AUDIO_BECOMING_NOISY` receiver → `controller.pause()`. Same `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH)` as the provider. Wire into the controller's play/stop.
- [ ] **Step 3: `TtsPlaybackService : MediaSessionService`** per recon §4b — `@AndroidEntryPoint`, `@Inject` the `@Singleton TtsPlayer`, build the `MediaSession` with `setSessionActivity(MainActivity PendingIntent)`, `onGetSession`, release in `onDestroy`. Provide `TtsPlayer` as `@Singleton` (its Looper = `Looper.getMainLooper()`).
- [ ] **Step 4: Manifest** per recon §2 — add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `POST_NOTIFICATIONS` permissions + the `<service android:name=".playback.TtsPlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="true">` with the `androidx.media3.session.MediaSessionService` intent-filter. Request `POST_NOTIFICATIONS` at runtime (API 33+) before first playback (in `MainActivity` or when play is tapped).
- [ ] **Step 5: Verify + device.** `./gradlew :app:assembleDebug` → SUCCESS. Device-validate (Task 12): notification + lock-screen controls, audio focus (pause on call/other media), headset-unplug pause. **Step 6: Commit** `feat(tts): media3 SimpleBasePlayer + MediaSessionService + self-managed audio focus`.

---

## Task 9: `ReaderDecorationController` refactor + tts highlight + auto-scroll

**Files:** Create `reader/ReaderDecorationController.kt`; Modify `reader/ReaderViewModel.kt`, `reader/ReaderScreen.kt`.

- [ ] **Step 1: Extract** `ReaderDecorationController` (holds the bound `EpubNavigatorFragment`; methods `applyBookmarks(list)`, `applyTts(locator?)` [group `"tts"`, rose Highlight via `SentenceLocators`/a single-decoration list, or clear when null], `runProbe`/`clearProbe`). Move the `applyDecorations(group=…)` calls out of `ReaderViewModel` into it; `ReaderViewModel` delegates. Keep behavior identical (bookmarks + probe still work).
- [ ] **Step 2: Observe the controller.** Inject `TtsPlaybackController` into `ReaderViewModel`; collect `controller.currentLocator` → `decorationController.applyTts(locator)` + (if `autoScroll`) `navigator.go(locator, animated=false)`. Only when `highlightOn`.
- [ ] **Step 3: Verify.** Existing reader tests stay green (`./gradlew :app:testDebugUnitTest`); `assembleDebug` SUCCESS. Highlight-follows-speech is device-validated (Task 12). **Step 4: Commit** `feat(tts): ReaderDecorationController + speech-driven sentence highlight + auto-scroll`.

---

## Task 10: Mini-player

**Files:** Create `playback/ui/MiniPlayer.kt`; Modify `ui/navigation/KanomApp.kt`, `reader/ReaderScreen.kt`.

- [ ] **Step 1: `MiniPlayer`** Composable — observes `TtsPlaybackController.state` (via `hiltViewModel`-less direct injection through a small `@HiltViewModel PlaybackViewModel` or `collectAsState` on an injected controller exposed via a CompositionLocal/VM): shows cover + current chapter/book title, the active voice chip (e.g. ไทย), play/pause + prev/next-sentence, a sleep-timer chip. Hidden when `state is Idle`. Primary/surface tokens (no rose). Preview-able with a fake state.
- [ ] **Step 2: Dock app-wide.** In `KanomApp`'s `Scaffold` render `MiniPlayer()` above the `NavigationBar` (a `Column` in `bottomBar`) so it shows on Library/Settings; in `ReaderScreen` overlay it at the bottom (reader has no bottom nav). Shown only when playback active.
- [ ] **Step 3: Verify + device.** `assembleDebug` SUCCESS; previews compile. Device: mini-player appears on play, controls work, persists across screens. **Step 4: Commit** `feat(tts): docked mini-player (transport + voice chip + sleep-timer chip)`.

---

## Task 11: Start triggers — play-from-current + long-press "Read from here"

**Files:** Modify `reader/ReaderViewModel.kt`, `reader/ReaderScreen.kt`, `reader/ReaderChrome.kt`.

- [ ] **Step 1: Play control** — add a play/pause action to `ReaderChrome` (e.g. `Icons.Filled.PlayArrow`/`Pause`) → `viewModel.toggleTts()`: if idle, `controller.start(bookId, fromLocator = navigator.firstVisibleElementLocator() ?: navigator.currentLocator.value)` then `controller.play()`; if playing, `controller.pause()`. (`firstVisibleElementLocator()` is `@ExperimentalReadiumApi suspend` — recon §8d.)
- [ ] **Step 2: Long-press "Read from here"** — add `selectionActionModeCallback` to the `EpubNavigatorFragment.Configuration` in `ReaderViewModel.load()` (recon §8c): a menu item "อ่านจากตรงนี้" whose action reads `(navigator as SelectableNavigator).currentSelection()?.locator`, calls `controller.start(bookId, it)` + `controller.play()` + `clearSelection()`. Our `ReaderChrome` `detectTapGestures` has no `onLongPress`, so long-press should already reach the WebView selection — **device-verify** the selection handles + ActionMode appear over the overlay (recon §8e gesture caveat). If they don't, the fallback is the play-from-current control (Step 1), which is reliable.
- [ ] **Step 3: Verify + device.** `assembleDebug` SUCCESS. Device: play button starts from the visible page; long-press a paragraph → "อ่านจากตรงนี้" → starts there. **Step 4: Commit** `feat(tts): start triggers — play-from-current + long-press Read-from-here`.

---

## Task 12: Whole-branch review + device matrix + finish

- [ ] **Step 1:** `./gradlew :text:pipeline:test :app:testDebugUnitTest :app:assembleDebug` → all green.
- [ ] **Step 2:** Final whole-branch review (opus) — focus: controller concurrency (single-Looper player vs the controller's coroutine scope; TTS-callback marshaling), audio-focus correctness, the two-publication lifecycle (reader display vs TTS), ttsLocator vs locator non-clobber, no rose misuse, `@UnstableApi`/opt-ins.
- [ ] **Step 3: On-device matrix (the spike's deferred checklist):** real TH/EN/JA/ZH **in-paragraph voice switching** with acceptable gaps; sentence highlight + auto-scroll-follow; background continues screen-off; lock-screen/notification/headset controls + audio focus; sleep timer + fade + end-of-chapter; **resume listening position**; Samsung `LANG_MISSING_DATA` fallback; `onRangeStart` availability (informs Phase-2 word highlight); long-press selection pass-through; POST_NOTIFICATIONS prompt on 13+. Resolve recon §unresolved (MediaItemData.uid ctor, segment-vs-chapter granularity, engine pinning, Thai voice availability).
- [ ] **Step 4:** Hand off to `superpowers:finishing-a-development-branch`.

---

## Self-Review

**Spec coverage:** §2 architecture → Tasks 5,6,8 (controller/source/player). §3 TtsProvider → Task 2. §4 controller → Tasks 5–6. §5 highlight + decoration refactor → Task 9. §6 media3 background → Task 8. §7 mini-player → Task 10. §8 sleep timer → Task 7. §9 voice routing → Task 3; start triggers (play-from-current + long-press) → Task 11. §10 data/deps (ttsLocator, media3) → Tasks 1,6. §11 testing + §12 risks → Task 12 + the per-task TDD/device gates. All spec sections covered.

**Placeholder scan:** the "inline spike" notes (Task 6 `Content.Iterator`, Task 8 `MediaItemData.uid`) are bounded recon-confirmations with the recon doc as source, not vague TODOs. Device-validated steps are explicit checklists. Where logic isn't JVM-testable (TextToSpeech, media3 service, streaming a real EPUB), that's stated with a device gate — matching the codebase's established reality.

**Type consistency:** `TtsProvider.speak(text, lang, voiceId, speedRate, pitch)`, `Voice(id,name,lang,needsNetwork,quality)`, `VoiceRouter.choose(lang, configs): VoiceChoice`, `SentenceLocators.forSentence(element, start, end)`, `SentenceUtterance(locator, segments)`, `TtsContentSource.stream(bookId, fromLocator): Flow<SentenceUtterance>`, `PlaybackState`, and `TtsPlaybackController`'s `state`/`currentLocator`/transport are referenced consistently across Tasks 2–11. `SpeechSegment(lang, spokenText)`/`SpokenSentence(start,end,originalText,segments)` match `:text:pipeline`. media3/`TextToSpeech`/Readium signatures match the recon doc.
