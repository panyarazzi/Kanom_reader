# Plan 08a — Now-Playing Screen + Per-Language Voice Settings — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two placeholder bottom-nav tabs with a real **Now-Playing screen** (cover, live "currently speaking" sentence card, transport, progress, quick chips) and a **Settings screen** (per-language voice/speed/pitch with audition preview, a Playback-prefs section, and a premium stub), wiring them to the playback engine Plan 07 already shipped.

**Architecture:** All logic plugs into existing `playback/` seams. Pure formatting/sample helpers are TDD'd first; the singleton `TtsPlaybackController` gains three small additions (a `currentSentenceText` StateFlow, a live voice-config collector, and a `preview` path); two narrow new seams (`VoiceCatalog`, `VoicePreviewer`) keep the new ViewModels testable; the screens expand the existing stubs and reuse the `ReaderSettingsSheet` control vocabulary + Anchan design system.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3 Expressive, Hilt, Coroutines/Flow, Coil 3, Readium 3.3.0 `Locator`, JUnit4 + Robolectric (`@Config(sdk=[35])`), DataStore.

## Global Constraints

_Copied from `kanom-tts-roadmap.md`; every task implicitly includes these._

- **Toolchain:** AGP 9.2.1 with **built-in Kotlin 2.2.10** — do **NOT** apply `org.jetbrains.kotlin.android` to `:app`. `src/main/kotlin` compiles for free.
- **Java 17 + core-library desugaring**; Compose BOM `2026.06.00` (material3 1.4.0 Expressive); Hilt 2.59.2 via **KSP**; Room 2.8.4; Coil 3.3.0; media3 1.10.1.
- **`Lang`** lives in `:text:pipeline` (`com.nextz.kanomtts.text.pipeline.Lang { TH, EN, JA, ZH }`); `SpeechSegment(val lang: Lang, val spokenText: String)` in the same module.
- **Threading:** `TtsPlaybackController` is the `@Singleton` single source of truth; ALL its mutable state lives on its `@PlaybackScope` `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. New controller state must follow the same rule — never read/write `configs`, `cursor`, `utterances`, or any `_mutable` flow off that scope.
- **Two publications:** sentence text for the card must be the **un-transformed spoken text** (`SpeechSegment.spokenText`) — never the ZWSP display text.
- **Reserved color:** `RoseHighlight #FF5E9C` / `KanomTheme.colors.highlight*` is reserved for "this is being spoken" — use it for the sentence card, nowhere else new.
- **Tests:** `:app` unit/Robolectric tests live in `app/src/test/kotlin/...`; JUnit4 (`org.junit.Test`, `org.junit.Assert.*`), backtick test names. Android-touching tests use `@Config(sdk = [35]) @RunWith(RobolectricTestRunner::class)`.
- **Test commands:** `./gradlew :app:testDebugUnitTest` (app), `./gradlew :text:pipeline:test :text:replacement:test` (text modules), `./gradlew :app:assembleDebug` (build), `./gradlew :app:lintDebug` (lint).
- **String formatting:** use `java.util.Locale.US` in numeric formatters so digits stay ASCII regardless of device locale (Thai locale would otherwise render Thai numerals).
- Device-only edges (real TTS engine, foreground service, real-book streaming) are validated on a device after the focused review — the established cadence; expect to harden post-build.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/nextz/kanomtts/playback/PlaybackFormatting.kt` — pure display/clamp/order helpers (Task 1).
- `app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceSamples.kt` — per-language preview sample strings (Task 1).
- `app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceCatalog.kt` — voice-enumeration seam (Task 3).
- `app/src/main/kotlin/com/nextz/kanomtts/playback/VoicePreviewer.kt` — preview seam (Task 4).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/VoiceSettingsViewModel.kt` (Task 5).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/VoiceLanguageEditor.kt` (Task 5).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModel.kt` (Task 6).
- `app/src/main/kotlin/com/nextz/kanomtts/playback/ui/NowPlayingViewModel.kt` (Task 7).
- Tests under `app/src/test/kotlin/com/nextz/kanomtts/...` per task.

**Modify:**
- `playback/VoiceConfigSupplier.kt` — `fun interface` → regular `interface` + `observe()` (Task 4).
- `playback/di/PlaybackModule.kt` — supplier provider body + `VoiceCatalog`/`VoicePreviewer` binds (Tasks 3, 4).
- `playback/SystemTtsProvider.kt` — implement `VoiceCatalog` (Task 3).
- `playback/TtsPlaybackController.kt` — `currentSentenceText`, live-config collector, `preview` (Task 4).
- `data/prefs/ReaderPreferences.kt` + the `FakeReaderPreferences` test double — add four setter declarations (Task 2).
- `ui/screens/SettingsScreen.kt` — expand stub (Task 6).
- `ui/screens/NowPlayingScreen.kt` — expand stub (Task 7).
- `playback/ui/MiniPlayer.kt` + `ui/navigation/KanomApp.kt` — mini-player `onExpand` → navigate (Task 7).

---

## Task 1: Pure formatting / sample helpers (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/PlaybackFormatting.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceSamples.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/playback/PlaybackFormattingTest.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/playback/VoiceSamplesTest.kt`

**Interfaces:**
- Consumes: `Lang`, `SpeechSegment` (`:text:pipeline`); `Voice`, `VoiceRouter` (same package); `VoiceConfigEntity` (`data.db`).
- Produces: `object PlaybackFormatting` with `SPEED_MIN/MAX=0.5f/3.0f`, `PITCH_MIN/MAX=0.5f/2.0f`, `clampSpeed`, `clampPitch`, `formatMultiplier`, `formatSleepRemaining`, `langLabel`, `progressFraction(Double?)`, `sentenceTextOf(List<SpeechSegment>)`, `orderVoices(List<Voice>)`, `effectiveSpeed(VoiceConfigEntity?)`, `effectivePitch(VoiceConfigEntity?)`. `object VoiceSamples.sampleFor(Lang): String`.

- [ ] **Step 1: Write the failing tests**

`PlaybackFormattingTest.kt`:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.SpeechSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFormattingTest {
    @Test fun `clampSpeed bounds to 0_5 and 3_0`() {
        assertEquals(0.5f, PlaybackFormatting.clampSpeed(0.1f))
        assertEquals(3.0f, PlaybackFormatting.clampSpeed(9f))
        assertEquals(1.5f, PlaybackFormatting.clampSpeed(1.5f))
    }

    @Test fun `clampPitch bounds to 0_5 and 2_0`() {
        assertEquals(0.5f, PlaybackFormatting.clampPitch(0f))
        assertEquals(2.0f, PlaybackFormatting.clampPitch(5f))
    }

    @Test fun `formatMultiplier uses one decimal and ascii digits`() {
        assertEquals("×1.0", PlaybackFormatting.formatMultiplier(1f))
        assertEquals("×1.5", PlaybackFormatting.formatMultiplier(1.5f))
    }

    @Test fun `formatSleepRemaining renders mm colon ss`() {
        assertEquals("", PlaybackFormatting.formatSleepRemaining(null))
        assertEquals("0:09", PlaybackFormatting.formatSleepRemaining(9_000))
        assertEquals("2:05", PlaybackFormatting.formatSleepRemaining(125_000))
        assertEquals("0:00", PlaybackFormatting.formatSleepRemaining(-5))
    }

    @Test fun `langLabel maps each language`() {
        assertEquals("ไทย", PlaybackFormatting.langLabel(Lang.TH))
        assertEquals("EN", PlaybackFormatting.langLabel(Lang.EN))
        assertEquals("日本", PlaybackFormatting.langLabel(Lang.JA))
        assertEquals("中文", PlaybackFormatting.langLabel(Lang.ZH))
    }

    @Test fun `progressFraction null is zero and clamps to unit range`() {
        assertEquals(0f, PlaybackFormatting.progressFraction(null))
        assertEquals(0.42f, PlaybackFormatting.progressFraction(0.42), 1e-6f)
        assertEquals(1f, PlaybackFormatting.progressFraction(1.7))
    }

    @Test fun `sentenceTextOf concatenates spoken segments`() {
        val segs = listOf(SpeechSegment(Lang.TH, "ผมชอบ "), SpeechSegment(Lang.EN, "coding"))
        assertEquals("ผมชอบ coding", PlaybackFormatting.sentenceTextOf(segs))
    }

    @Test fun `orderVoices sorts by quality desc then name`() {
        val a = Voice("a", "Alpha", Lang.TH, false, 300)
        val b = Voice("b", "Bravo", Lang.TH, false, 500)
        val c = Voice("c", "Charlie", Lang.TH, false, 500)
        assertEquals(listOf(b, c, a), PlaybackFormatting.orderVoices(listOf(a, c, b)))
    }

    @Test fun `effective speed and pitch fall back to defaults`() {
        assertEquals(VoiceRouter.DEFAULT_SPEED, PlaybackFormatting.effectiveSpeed(null))
        assertEquals(VoiceRouter.DEFAULT_PITCH, PlaybackFormatting.effectivePitch(null))
        val cfg = VoiceConfigEntity("TH", "system", null, 2.0f, 0.8f)
        assertEquals(2.0f, PlaybackFormatting.effectiveSpeed(cfg))
        assertEquals(0.8f, PlaybackFormatting.effectivePitch(cfg))
    }
}
```

`VoiceSamplesTest.kt`:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSamplesTest {
    @Test fun `every language has a non-blank sample`() {
        Lang.entries.forEach { assertTrue(it.name, VoiceSamples.sampleFor(it).isNotBlank()) }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.playback.PlaybackFormattingTest" --tests "com.nextz.kanomtts.playback.VoiceSamplesTest"`
Expected: FAIL — `PlaybackFormatting` / `VoiceSamples` unresolved.

- [ ] **Step 3: Write the implementations**

`PlaybackFormatting.kt`:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.SpeechSegment
import java.util.Locale

/** Pure, deterministic display/clamp/order helpers for the player + voice settings UI. */
object PlaybackFormatting {
    const val SPEED_MIN = 0.5f
    const val SPEED_MAX = 3.0f
    const val PITCH_MIN = 0.5f
    const val PITCH_MAX = 2.0f

    fun clampSpeed(v: Float): Float = v.coerceIn(SPEED_MIN, SPEED_MAX)
    fun clampPitch(v: Float): Float = v.coerceIn(PITCH_MIN, PITCH_MAX)

    fun formatMultiplier(v: Float): String = "×%.1f".format(Locale.US, v)

    fun formatSleepRemaining(ms: Long?): String {
        if (ms == null) return ""
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, totalSec / 60, totalSec % 60)
    }

    fun langLabel(lang: Lang): String = when (lang) {
        Lang.TH -> "ไทย"
        Lang.EN -> "EN"
        Lang.JA -> "日本"
        Lang.ZH -> "中文"
    }

    /** Book progress 0..1 from Readium totalProgression (nullable). */
    fun progressFraction(totalProgression: Double?): Float =
        (totalProgression ?: 0.0).toFloat().coerceIn(0f, 1f)

    /** Reconstruct the spoken sentence from its per-script-run segments (un-transformed text). */
    fun sentenceTextOf(segments: List<SpeechSegment>): String =
        segments.joinToString("") { it.spokenText }

    /** Best-first: higher quality, then name. */
    fun orderVoices(voices: List<Voice>): List<Voice> =
        voices.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })

    fun effectiveSpeed(config: VoiceConfigEntity?): Float = config?.speed ?: VoiceRouter.DEFAULT_SPEED
    fun effectivePitch(config: VoiceConfigEntity?): Float = config?.pitch ?: VoiceRouter.DEFAULT_PITCH
}
```

`VoiceSamples.kt`:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

/** Fixed phrases spoken when auditioning a voice in settings. */
object VoiceSamples {
    fun sampleFor(lang: Lang): String = when (lang) {
        Lang.TH -> "สวัสดีค่ะ นี่คือตัวอย่างเสียงอ่านภาษาไทย"
        Lang.EN -> "Hello, this is a sample of the reading voice."
        Lang.JA -> "こんにちは。これは読み上げ音声のサンプルです。"
        Lang.ZH -> "你好，这是朗读语音的示例。"
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.playback.PlaybackFormattingTest" --tests "com.nextz.kanomtts.playback.VoiceSamplesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/PlaybackFormatting.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceSamples.kt \
        app/src/test/kotlin/com/nextz/kanomtts/playback/PlaybackFormattingTest.kt \
        app/src/test/kotlin/com/nextz/kanomtts/playback/VoiceSamplesTest.kt
git commit -m "feat(playback): pure formatting + voice-sample helpers for 08a"
```

---

## Task 2: Expose the four Playback-prefs setters on `ReaderPreferences`

The concrete `PreferencesRepository` **already implements** `setSkipUrls`, `setHighlightOn`, `setAutoScroll`, `setSleepTimerDefaultMin` (with DataStore keys + Flow reads). They are simply absent from the `ReaderPreferences` interface, so callers can't reach them. This task adds the four declarations and updates the test fake.

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/prefs/ReaderPreferences.kt`
- Modify: the `FakeReaderPreferences` test double (locate under `app/src/test/kotlin/...`; referenced by `ReaderPreferences`'s KDoc).
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/prefs/PlaybackPrefsSettersTest.kt`

**Interfaces:**
- Produces: `ReaderPreferences.setSkipUrls(Boolean)`, `setHighlightOn(Boolean)`, `setAutoScroll(Boolean)`, `setSleepTimerDefaultMin(Int)`.

- [ ] **Step 1: Write the failing test**

`PlaybackPrefsSettersTest.kt` (uses the existing fake — confirm its constructor when you open it; this assumes `FakeReaderPreferences()` backs a `MutableStateFlow<KanomPreferences>` named `preferences`):
```kotlin
package com.nextz.kanomtts.data.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPrefsSettersTest {
    @Test fun `playback setters write through the interface`() = runTest {
        val prefs: ReaderPreferences = FakeReaderPreferences()
        prefs.setSkipUrls(true)
        prefs.setHighlightOn(false)
        prefs.setAutoScroll(false)
        prefs.setSleepTimerDefaultMin(45)
        val p = prefs.preferences.first()
        assertEquals(true, p.skipUrls)
        assertEquals(false, p.highlightOn)
        assertEquals(false, p.autoScroll)
        assertEquals(45, p.sleepTimerDefaultMin)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.data.prefs.PlaybackPrefsSettersTest"`
Expected: FAIL — `setSkipUrls` (etc.) not a member of `ReaderPreferences`, and/or `FakeReaderPreferences` doesn't implement them.

- [ ] **Step 3: Add the interface declarations + fake overrides**

In `ReaderPreferences.kt`, add inside the interface (after `setPageAnimation`):
```kotlin
    // Playback / app prefs (impl already exists in PreferencesRepository)
    suspend fun setSkipUrls(v: Boolean)
    suspend fun setHighlightOn(v: Boolean)
    suspend fun setAutoScroll(v: Boolean)
    suspend fun setSleepTimerDefaultMin(v: Int)
```

In `FakeReaderPreferences`, add overrides mutating its backing state (match the fake's existing style — it already updates a `MutableStateFlow<KanomPreferences>` via `.update { it.copy(...) }`):
```kotlin
    override suspend fun setSkipUrls(v: Boolean) = update { it.copy(skipUrls = v) }
    override suspend fun setHighlightOn(v: Boolean) = update { it.copy(highlightOn = v) }
    override suspend fun setAutoScroll(v: Boolean) = update { it.copy(autoScroll = v) }
    override suspend fun setSleepTimerDefaultMin(v: Int) = update { it.copy(sleepTimerDefaultMin = v) }
```
The concrete `PreferencesRepository` already satisfies these (lines 67–71) — no change needed there. If the compiler reports its methods aren't `override`, add the `override` keyword to those four existing functions.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.data.prefs.PlaybackPrefsSettersTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/prefs/ReaderPreferences.kt app/src/test/kotlin/com/nextz/kanomtts/
git commit -m "feat(prefs): expose skipUrls/highlightOn/autoScroll/sleepDefault setters on ReaderPreferences"
```

---

## Task 3: `VoiceCatalog` voice-enumeration seam

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceCatalog.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/SystemTtsProvider.kt:23-26` (add supertype)
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackModule.kt` (add `@Binds`)

**Interfaces:**
- Produces: `interface VoiceCatalog { suspend fun voices(lang: Lang): List<Voice> }`, bound to `SystemTtsProvider`.

- [ ] **Step 1: Create the interface**

`VoiceCatalog.kt`:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

/** Narrow seam for enumerating installed system voices — lets settings VMs avoid the whole provider. */
interface VoiceCatalog {
    suspend fun voices(lang: Lang): List<Voice>
}
```

- [ ] **Step 2: Make `SystemTtsProvider` implement it**

`SystemTtsProvider` already has `override suspend fun voices(lang: Lang): List<Voice>` (from `TtsProvider`); the same method satisfies `VoiceCatalog`. Add the supertype:
```kotlin
// app/src/main/kotlin/com/nextz/kanomtts/playback/SystemTtsProvider.kt:23-26
@Singleton
class SystemTtsProvider @Inject constructor(
    @ApplicationContext context: Context,
) : TtsProvider, VoiceCatalog {
```

- [ ] **Step 3: Bind it in `PlaybackModule`**

Alongside the existing `@Binds @Singleton abstract fun ttsProvider(impl: SystemTtsProvider): TtsProvider` (lines 31–33), add:
```kotlin
    @Binds
    @Singleton
    abstract fun voiceCatalog(impl: SystemTtsProvider): VoiceCatalog
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Hilt graph resolves the new binding).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceCatalog.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/SystemTtsProvider.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackModule.kt
git commit -m "feat(playback): VoiceCatalog seam bound to SystemTtsProvider"
```

---

## Task 4: Controller seams — `currentSentenceText`, live config, `preview`

Integration change to the singleton. **Review focus:** all new state stays on `@PlaybackScope`; the live-config collector and `configs` mutation never run off that scope; `preview` doesn't leave the engine mid-utterance for the running session. Validated on device after the focused review (no heavy controller unit test — matches `PlaybackViewModel`'s device-validated pattern; the pure parts are covered by Task 1).

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceConfigSupplier.kt` (interface shape)
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackModule.kt:55-64` (provider body)
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/VoicePreviewer.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/TtsPlaybackController.kt` (state field, loop, init collector, preview, class supertype, reset sites)

**Interfaces:**
- Consumes: `PlaybackFormatting.sentenceTextOf`, `PlaybackFormatting.clampSpeed/clampPitch`, `VoiceSamples.sampleFor` (Task 1).
- Produces: `TtsPlaybackController.currentSentenceText: StateFlow<String?>`; `VoiceConfigSupplier.observe(): Flow<Map<String, VoiceConfigEntity>>`; `interface VoicePreviewer { suspend fun preview(lang, voiceId, speed, pitch) }` implemented by the controller.

- [ ] **Step 1: Convert `VoiceConfigSupplier` to a regular interface with `observe()`**

Replace the file body:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.data.db.VoiceConfigEntity
import kotlinx.coroutines.flow.Flow

interface VoiceConfigSupplier {
    /** One-shot snapshot, keyed by Lang.name. Used at session start. */
    suspend fun load(): Map<String, VoiceConfigEntity>

    /** Live stream of the config map — emits on every edit so playback applies changes at the next segment. */
    fun observe(): Flow<Map<String, VoiceConfigEntity>>
}
```

- [ ] **Step 2: Update the Hilt provider to implement both**

`PlaybackModule.kt` — replace the `voiceConfigSupplier` provider (lines 55–64) lambda with an object:
```kotlin
        @Provides
        @Singleton
        fun voiceConfigSupplier(repo: VoiceConfigRepository): VoiceConfigSupplier =
            object : VoiceConfigSupplier {
                override suspend fun load(): Map<String, VoiceConfigEntity> =
                    repo.observeAll().first().associateBy { it.lang }

                override fun observe(): Flow<Map<String, VoiceConfigEntity>> =
                    repo.observeAll().map { list -> list.associateBy { it.lang } }
            }
```
Add imports if missing: `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.map`, `com.nextz.kanomtts.data.db.VoiceConfigEntity`. (Then grep for any other `VoiceConfigSupplier {` SAM-lambda usages — e.g. test fakes — and convert them to `object : VoiceConfigSupplier { ... }`.)

- [ ] **Step 3: Create the `VoicePreviewer` seam**

`VoicePreviewer.kt`:
```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

/** Audition a voice with a fixed sample phrase; pauses active playback and stays paused. */
interface VoicePreviewer {
    suspend fun preview(lang: Lang, voiceId: String?, speed: Float, pitch: Float)
}
```

- [ ] **Step 4: Add `currentSentenceText` state + the live-config collector + `preview` to the controller**

In `TtsPlaybackController.kt`:

(a) Add `VoicePreviewer` to the class supertypes:
```kotlin
) : TtsPlaybackSource, VoicePreviewer
```

(b) Add the new state field next to `_currentLang` (lines ~115–116):
```kotlin
    /** Text of the sentence currently being spoken; null when idle/stopped. Drives the Now-Playing card. */
    private val _currentSentenceText = MutableStateFlow<String?>(null)
    val currentSentenceText: StateFlow<String?> = _currentSentenceText.asStateFlow()
```

(c) Add a live-config collector. If the controller has an `init {}` block (where the tts-locator debounce + provider-events collectors are launched), add this line there; otherwise create an `init {}`:
```kotlin
        // Keep the cached config map live so voice/speed/pitch edits apply at the next segment.
        scope.launch { voiceConfig.observe().collect { configs = it } }
```
Keep the existing `configs = voiceConfig.load()` lines in `start()`/`startAndPlay()` — they guarantee `configs` is populated synchronously before the first segment; the collector then overwrites it on later edits. Both run on `scope` (Main), so no data race.

(d) In `runSpeakLoop`, immediately after `val utterance = utterances[cursor.value]` (line ~427), set the card text:
```kotlin
            _currentSentenceText.value = PlaybackFormatting.sentenceTextOf(utterance.segments)
```

(e) Clear it everywhere `_currentLang.value = null` is already set — the error branch (line ~466) and the terminal-Idle block (line ~483):
```kotlin
            _currentLang.value = null
            _currentSentenceText.value = null
```
Also set `_currentSentenceText.value = null` next to `_currentLocator.value = null` in the `start()` and `startAndPlay()` setup blocks, and in `tearDown()` wherever it resets `_state`/`_currentLocator`.

(f) Add the `preview` implementation (e.g. below `setSleepTimer`):
```kotlin
    override suspend fun preview(lang: Lang, voiceId: String?, speed: Float, pitch: Float) {
        // Free the single TextToSpeech: pause any active session and wait for the loop to stop.
        if (_state.value is PlaybackState.Playing) {
            pause()
            playJob?.join()
        }
        try {
            provider.speak(
                text = VoiceSamples.sampleFor(lang),
                lang = lang,
                voiceId = voiceId,
                speedRate = PlaybackFormatting.clampSpeed(speed),
                pitch = PlaybackFormatting.clampPitch(pitch),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _providerError.tryEmit("Preview failed: ${e.message}")
        }
    }
```

- [ ] **Step 5: Bind `VoicePreviewer` to the controller**

In `PlaybackModule` (the `@Binds` abstract section), add:
```kotlin
    @Binds
    @Singleton
    abstract fun voicePreviewer(impl: TtsPlaybackController): VoicePreviewer
```

- [ ] **Step 6: Build + run the full app test suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all existing tests still pass (the supplier-interface change compiles; no behavioral regressions).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/VoiceConfigSupplier.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/VoicePreviewer.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/TtsPlaybackController.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/di/PlaybackModule.kt
git commit -m "feat(playback): currentSentenceText + live voice-config + preview seam on controller"
```

**Device validation (after review):** play a book → sentence card text tracks speech; change a voice's speed in settings mid-session → next sentence speeds up; tap preview while playing → playback pauses, sample speaks, stays paused; resume → continues cleanly.

---

## Task 5: `VoiceSettingsViewModel` + `VoiceLanguageEditor`

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/VoiceSettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/VoiceLanguageEditor.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/settings/VoiceSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `VoiceCatalog` (Task 3), `VoicePreviewer` (Task 4), `VoiceConfigRepository` (`data.repository`), `PlaybackFormatting` (Task 1), `VoiceRouter.DEFAULT_*`.
- Produces: `VoiceSettingsViewModel` with `uiState: StateFlow<VoiceSettingsUiState>`, `loadVoices(Lang)`, `selectVoice(Lang, String?)`, `setSpeed(Lang, Float)`, `setPitch(Lang, Float)`, `preview(Lang)`; data classes `VoiceSettingsUiState(languages: List<VoiceLangState>)` and `VoiceLangState(lang, label, voices, selectedVoiceId, speed, pitch)`; stateless `VoiceLanguageEditor(state, onSelectVoice, onSpeed, onPitch, onPreview)` + a `@Composable VoiceLanguageEditor(lang, viewModel = hiltViewModel())` wrapper.

- [ ] **Step 1: Write the failing test**

`VoiceSettingsViewModelTest.kt`:
```kotlin
package com.nextz.kanomtts.feature.settings

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import android.content.Context
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.playback.VoiceCatalog
import com.nextz.kanomtts.playback.VoicePreviewer
import com.nextz.kanomtts.text.pipeline.Lang
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class VoiceSettingsViewModelTest {
    private lateinit var db: KanomDatabase
    private lateinit var repo: VoiceConfigRepository

    private val catalog = VoiceCatalog { lang ->
        listOf(
            Voice("low", "Low", lang, false, 100),
            Voice("hi", "High", lang, false, 500),
        )
    }
    private val previews = mutableListOf<Triple<Lang, Float, Float>>()
    private val previewer = VoicePreviewer { lang, _, speed, pitch ->
        previews += Triple(lang, speed, pitch)
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KanomDatabase::class.java).allowMainThreadQueries().build()
        repo = VoiceConfigRepository(db.voiceConfigDao())
    }
    @After fun tearDown() { db.close() }

    private fun vm() = VoiceSettingsViewModel(catalog, repo, previewer)

    @Test fun `loadVoices populates ordered voices for a language`() = runTest {
        val vm = vm()
        vm.uiState.first() // start collecting
        vm.loadVoices(Lang.TH)
        val th = vm.uiState.first().languages.first { it.lang == Lang.TH }
        assertEquals(listOf("hi", "low"), th.voices.map { it.id }) // quality desc
    }

    @Test fun `setSpeed clamps and persists`() = runTest {
        val vm = vm()
        vm.uiState.first()
        vm.setSpeed(Lang.TH, 9f)
        assertEquals(3.0f, repo.get("TH")!!.speed)
    }

    @Test fun `selectVoice persists the voice id`() = runTest {
        val vm = vm()
        vm.uiState.first()
        vm.selectVoice(Lang.EN, "hi")
        assertEquals("hi", repo.get("EN")!!.voiceId)
    }

    @Test fun `preview uses the saved effective config`() = runTest {
        val vm = vm()
        repo.set(VoiceConfigEntity("TH", "system", "hi", 2.0f, 0.8f))
        vm.preview(Lang.TH)
        assertEquals(Triple(Lang.TH, 2.0f, 0.8f), previews.single())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.settings.VoiceSettingsViewModelTest"`
Expected: FAIL — `VoiceSettingsViewModel` unresolved. (Confirm `db.voiceConfigDao()` is the actual DAO accessor on `KanomDatabase`; adjust if named differently.)

- [ ] **Step 3: Implement the ViewModel**

`VoiceSettingsViewModel.kt`:
```kotlin
package com.nextz.kanomtts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.playback.VoiceCatalog
import com.nextz.kanomtts.playback.VoicePreviewer
import com.nextz.kanomtts.playback.VoiceRouter
import com.nextz.kanomtts.text.pipeline.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceLangState(
    val lang: Lang,
    val label: String,
    val voices: List<Voice> = emptyList(),
    val selectedVoiceId: String? = null,
    val speed: Float = VoiceRouter.DEFAULT_SPEED,
    val pitch: Float = VoiceRouter.DEFAULT_PITCH,
)

data class VoiceSettingsUiState(val languages: List<VoiceLangState> = emptyList())

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val catalog: VoiceCatalog,
    private val repo: VoiceConfigRepository,
    private val previewer: VoicePreviewer,
) : ViewModel() {

    private val _voicesByLang = MutableStateFlow<Map<Lang, List<Voice>>>(emptyMap())

    val uiState: StateFlow<VoiceSettingsUiState> =
        combine(repo.observeAll(), _voicesByLang) { configs, voices ->
            val byLang = configs.associateBy { it.lang }
            VoiceSettingsUiState(
                languages = Lang.entries.map { lang ->
                    val cfg = byLang[lang.name]
                    VoiceLangState(
                        lang = lang,
                        label = PlaybackFormatting.langLabel(lang),
                        voices = voices[lang].orEmpty(),
                        selectedVoiceId = cfg?.voiceId,
                        speed = PlaybackFormatting.effectiveSpeed(cfg),
                        pitch = PlaybackFormatting.effectivePitch(cfg),
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoiceSettingsUiState())

    fun loadVoices(lang: Lang) = viewModelScope.launch {
        val ordered = PlaybackFormatting.orderVoices(catalog.voices(lang))
        _voicesByLang.update { it + (lang to ordered) }
    }

    fun selectVoice(lang: Lang, voiceId: String?) = upsert(lang) { it.copy(voiceId = voiceId) }
    fun setSpeed(lang: Lang, v: Float) = upsert(lang) { it.copy(speed = PlaybackFormatting.clampSpeed(v)) }
    fun setPitch(lang: Lang, v: Float) = upsert(lang) { it.copy(pitch = PlaybackFormatting.clampPitch(v)) }

    private fun upsert(lang: Lang, edit: (VoiceConfigEntity) -> VoiceConfigEntity) = viewModelScope.launch {
        val current = repo.get(lang.name) ?: VoiceConfigEntity(
            lang = lang.name, providerId = "system", voiceId = null,
            speed = VoiceRouter.DEFAULT_SPEED, pitch = VoiceRouter.DEFAULT_PITCH,
        )
        repo.set(edit(current))
    }

    fun preview(lang: Lang) = viewModelScope.launch {
        val cfg = repo.get(lang.name)
        previewer.preview(lang, cfg?.voiceId, PlaybackFormatting.effectiveSpeed(cfg), PlaybackFormatting.effectivePitch(cfg))
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.settings.VoiceSettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement the stateless + Hilt-wrapper editor composable**

`VoiceLanguageEditor.kt` — reuses the `ReaderSettingsSheet` control vocabulary (`SectionLabel` private helper, `FilterChip`, `Slider`, `Button`). Render an empty-voices hint when `voices` is empty:
```kotlin
package com.nextz.kanomtts.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.text.pipeline.Lang

/** Hilt wrapper — load voices for [lang] on first composition, render the editor for it. */
@Composable
fun VoiceLanguageEditor(
    lang: Lang,
    modifier: Modifier = Modifier,
    viewModel: VoiceSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(lang) { viewModel.loadVoices(lang) }
    val langState = state.languages.firstOrNull { it.lang == lang } ?: return
    VoiceLanguageEditor(
        state = langState,
        onSelectVoice = { viewModel.selectVoice(lang, it) },
        onSpeed = { viewModel.setSpeed(lang, it) },
        onPitch = { viewModel.setPitch(lang, it) },
        onPreview = { viewModel.preview(lang) },
        modifier = modifier,
    )
}

/** Stateless — voice picker + speed/pitch sliders + audition button. */
@Composable
fun VoiceLanguageEditor(
    state: VoiceLangState,
    onSelectVoice: (String?) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("เสียง", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.voices.isEmpty()) {
            Text("ไม่พบเสียง — ติดตั้งเสียงในระบบ", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                state.voices.forEach { v ->
                    FilterChip(
                        selected = state.selectedVoiceId == v.id,
                        onClick = { onSelectVoice(v.id) },
                        label = { Text(v.name + if (v.needsNetwork) " ☁" else "") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("ความเร็ว: ${PlaybackFormatting.formatMultiplier(state.speed)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = state.speed, onValueChange = onSpeed,
            valueRange = PlaybackFormatting.SPEED_MIN..PlaybackFormatting.SPEED_MAX, modifier = Modifier.fillMaxWidth())

        Text("ระดับเสียง: ${PlaybackFormatting.formatMultiplier(state.pitch)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = state.pitch, onValueChange = onPitch,
            valueRange = PlaybackFormatting.PITCH_MIN..PlaybackFormatting.PITCH_MAX, modifier = Modifier.fillMaxWidth())

        Button(onClick = onPreview, modifier = Modifier.padding(top = 4.dp)) { Text("เล่นตัวอย่าง") }
    }
}
```

- [ ] **Step 6: Build to verify the composable compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/settings/ app/src/test/kotlin/com/nextz/kanomtts/feature/settings/
git commit -m "feat(settings): VoiceSettingsViewModel + reusable VoiceLanguageEditor"
```

---

## Task 6: Settings screen — Voices + Playback section + premium stub

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/screens/SettingsScreen.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ReaderPreferences` (Task 2), `KanomPreferences`, `VoiceLanguageEditor` (Task 5), `PlaybackFormatting`.
- Produces: `PlaybackSettingsViewModel(uiState: StateFlow<KanomPreferences>, setSkipUrls, setAutoScroll, setHighlightOn, setSleepTimerDefaultMin)`; the populated `SettingsScreen`.

- [ ] **Step 1: Write the failing test**

`PlaybackSettingsViewModelTest.kt`:
```kotlin
package com.nextz.kanomtts.feature.settings

import com.nextz.kanomtts.data.prefs.FakeReaderPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSettingsViewModelTest {
    @Test fun `toggles write through to prefs`() = runTest {
        val prefs = FakeReaderPreferences()
        val vm = PlaybackSettingsViewModel(prefs)
        vm.setSkipUrls(true)
        vm.setSleepTimerDefaultMin(20)
        val p = prefs.preferences.first()
        assertEquals(true, p.skipUrls)
        assertEquals(20, p.sleepTimerDefaultMin)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModelTest"`
Expected: FAIL — `PlaybackSettingsViewModel` unresolved.

- [ ] **Step 3: Implement `PlaybackSettingsViewModel`**

```kotlin
package com.nextz.kanomtts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaybackSettingsViewModel @Inject constructor(
    private val prefs: ReaderPreferences,
) : ViewModel() {
    val uiState: StateFlow<KanomPreferences> =
        prefs.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KanomPreferences())

    fun setSkipUrls(v: Boolean) = viewModelScope.launch { prefs.setSkipUrls(v) }
    fun setAutoScroll(v: Boolean) = viewModelScope.launch { prefs.setAutoScroll(v) }
    fun setHighlightOn(v: Boolean) = viewModelScope.launch { prefs.setHighlightOn(v) }
    fun setSleepTimerDefaultMin(v: Int) = viewModelScope.launch { prefs.setSleepTimerDefaultMin(v) }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Expand `SettingsScreen`**

Replace the placeholder body. Keep the existing function signature (`SettingsScreen(modifier, contentPadding)`) so the NavHost call is unchanged; pull both ViewModels via `hiltViewModel()`. A language selector reveals one `VoiceLanguageEditor`; Playback section uses `Switch` rows + a sleep `Slider`; premium row is a coming-soon stub.
```kotlin
package com.nextz.kanomtts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModel
import com.nextz.kanomtts.feature.settings.VoiceLanguageEditor
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.text.pipeline.Lang

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    playbackVm: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val prefs by playbackVm.uiState.collectAsState()
    var lang by remember { mutableStateOf(Lang.TH) }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("เสียงพากย์", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
        Row {
            Lang.entries.forEach { l ->
                FilterChip(
                    selected = lang == l,
                    onClick = { lang = l },
                    label = { Text(PlaybackFormatting.langLabel(l)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        VoiceLanguageEditor(lang = lang)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("การเล่น", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        ToggleRow("ข้ามลิงก์ (URL)", prefs.skipUrls, playbackVm::setSkipUrls)
        ToggleRow("เลื่อนตามอัตโนมัติ", prefs.autoScroll, playbackVm::setAutoScroll)
        ToggleRow("ไฮไลต์ประโยคที่กำลังอ่าน", prefs.highlightOn, playbackVm::setHighlightOn)
        Text("ตั้งเวลานอนเริ่มต้น: ${prefs.sleepTimerDefaultMin} นาที",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = prefs.sleepTimerDefaultMin.toFloat(),
            onValueChange = { playbackVm.setSleepTimerDefaultMin(it.toInt()) },
            valueRange = 5f..120f, steps = 22, modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("Neural · พรีเมียม — เร็วๆ นี้",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
```

- [ ] **Step 6: Build to verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModel.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/screens/SettingsScreen.kt \
        app/src/test/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModelTest.kt
git commit -m "feat(settings): Settings screen — voices + playback prefs + premium stub"
```

---

## Task 7: Now-Playing screen + ViewModel + mini-player expand

Integration + UI. The ViewModel injects the concrete `TtsPlaybackController` (the device-validated pattern `PlaybackViewModel` already uses); its derivations route through the Task-1 helpers, which carry the unit coverage. The screen reuses Coil cover rendering, the reserved rose for the sentence card, and `VoiceLanguageEditor` in the language-switcher sheet.

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/ui/NowPlayingViewModel.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/screens/NowPlayingScreen.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/ui/MiniPlayer.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt`

**Interfaces:**
- Consumes: `TtsPlaybackController` (`state`, `currentSentenceText`, `currentLang`, `currentLocator`, `sleepRemaining`, transport, `setSleepTimer`), `BookReader.get`, `VoiceConfigRepository`, `PlaybackFormatting`, `VoiceLanguageEditor`.
- Produces: `NowPlayingViewModel(uiState: StateFlow<NowPlayingUiState>, playPause, next, prev, stop, setSleep, nudgeSpeed, nudgePitch)`; `NowPlayingUiState(present, isPlaying, title, author, coverPath, sentenceText, langLabel, progress, sleepRemainingMs, speedDisplay, pitchDisplay, currentLang)`; `MiniPlayer(..., onExpand)` and `MiniPlayerBar(..., onExpand)`.

- [ ] **Step 1: Implement `NowPlayingViewModel`**

```kotlin
package com.nextz.kanomtts.playback.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.PlaybackState
import com.nextz.kanomtts.playback.TtsPlaybackController
import com.nextz.kanomtts.playback.VoiceRouter
import com.nextz.kanomtts.text.pipeline.Lang
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator

data class NowPlayingUiState(
    val present: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val author: String? = null,
    val coverPath: String? = null,
    val sentenceText: String? = null,
    val langLabel: String? = null,
    val progress: Float = 0f,
    val sleepRemainingMs: Long? = null,
    val speedDisplay: String = "×1.0",
    val pitchDisplay: String = "×1.0",
    val currentLang: Lang? = null,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val controller: TtsPlaybackController,
    private val books: BookReader,
    private val repo: VoiceConfigRepository,
) : ViewModel() {

    private data class Core(
        val state: PlaybackState, val sentence: String?, val lang: Lang?,
        val locator: Locator?, val sleep: Long?,
    )

    private val core = combine(
        controller.state, controller.currentSentenceText, controller.currentLang,
        controller.currentLocator, controller.sleepRemaining,
    ) { state, sentence, lang, locator, sleep -> Core(state, sentence, lang, locator, sleep) }

    val uiState: StateFlow<NowPlayingUiState> =
        combine(core, repo.observeAll()) { c, configs ->
            val bookId = (c.state as? PlaybackState.Playing)?.bookId
                ?: (c.state as? PlaybackState.Paused)?.bookId
            val book = bookId?.let { books.get(it) }
            val cfg = c.lang?.let { l -> configs.firstOrNull { it.lang == l.name } }
            NowPlayingUiState(
                present = bookId != null,
                isPlaying = c.state is PlaybackState.Playing,
                title = book?.title ?: "",
                author = book?.author,
                coverPath = book?.coverPath,
                sentenceText = c.sentence,
                langLabel = c.lang?.let { PlaybackFormatting.langLabel(it) },
                progress = PlaybackFormatting.progressFraction(c.locator?.locations?.totalProgression),
                sleepRemainingMs = c.sleep,
                speedDisplay = PlaybackFormatting.formatMultiplier(PlaybackFormatting.effectiveSpeed(cfg)),
                pitchDisplay = PlaybackFormatting.formatMultiplier(PlaybackFormatting.effectivePitch(cfg)),
                currentLang = c.lang,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

    fun playPause() { if (uiState.value.isPlaying) controller.pause() else controller.play() }
    fun next() = controller.nextSentence()
    fun prev() = controller.previousSentence()
    fun stop() = controller.stop()
    fun setSleep(min: Int?) = controller.setSleepTimer(min)

    fun nudgeSpeed(delta: Float) = nudge { it.copy(speed = PlaybackFormatting.clampSpeed(it.speed + delta)) }
    fun nudgePitch(delta: Float) = nudge { it.copy(pitch = PlaybackFormatting.clampPitch(it.pitch + delta)) }

    private fun nudge(edit: (VoiceConfigEntity) -> VoiceConfigEntity) {
        val lang = uiState.value.currentLang ?: return
        viewModelScope.launch {
            val current = repo.get(lang.name) ?: VoiceConfigEntity(
                lang = lang.name, providerId = "system", voiceId = null,
                speed = VoiceRouter.DEFAULT_SPEED, pitch = VoiceRouter.DEFAULT_PITCH,
            )
            repo.set(edit(current))
        }
    }
}
```
(Verify `PlaybackState`'s package + that `Playing`/`Paused` expose `bookId` — confirmed in recon. If `combine` of 5 flows needs an explicit import, it's `kotlinx.coroutines.flow.combine`.)

- [ ] **Step 2: Build to verify the ViewModel compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Expand `NowPlayingScreen`** (keep its `(modifier, contentPadding)` signature so the NavHost call is unchanged)

```kotlin
package com.nextz.kanomtts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.nextz.kanomtts.feature.settings.VoiceLanguageEditor
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.ui.NowPlayingViewModel
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.ui.theme.KanomTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var sheetLang by remember { mutableStateOf<Lang?>(null) }

    if (!state.present) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("ยังไม่มีการเล่น", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = state.coverPath, contentDescription = null,
            modifier = Modifier.size(220.dp).clip(MaterialTheme.shapes.large),
        )
        Spacer(Modifier.height(16.dp))
        Text(state.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        state.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }

        Spacer(Modifier.height(20.dp))
        // Reserved rose: "this is being spoken"
        Surface(
            color = KanomTheme.colors.highlightContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                state.sentenceText ?: "…",
                style = MaterialTheme.typography.titleMedium,
                color = KanomTheme.colors.onHighlight,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(viewModel::prev) { Icon(Icons.Filled.SkipPrevious, "ก่อนหน้า") }
            FilledIconButton(viewModel::playPause, modifier = Modifier.size(64.dp)) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "หยุดชั่วคราว" else "เล่น")
            }
            IconButton(viewModel::next) { Icon(Icons.Filled.SkipNext, "ถัดไป") }
            IconButton(viewModel::stop) { Icon(Icons.Filled.Stop, "หยุด") }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { viewModel.nudgeSpeed(-0.1f) }, label = { Text("ช้าลง") })
            Text("ความเร็ว ${state.speedDisplay}", style = MaterialTheme.typography.labelMedium)
            AssistChip(onClick = { viewModel.nudgeSpeed(0.1f) }, label = { Text("เร็วขึ้น") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { viewModel.nudgePitch(-0.1f) }, label = { Text("ต่ำลง") })
            Text("ระดับเสียง ${state.pitchDisplay}", style = MaterialTheme.typography.labelMedium)
            AssistChip(onClick = { viewModel.nudgePitch(0.1f) }, label = { Text("สูงขึ้น") })
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { viewModel.setSleep(15) },
                label = { Text(state.sleepRemainingMs?.let { "💤 " + PlaybackFormatting.formatSleepRemaining(it) } ?: "💤 ตั้งเวลานอน") })
            state.currentLang?.let { l ->
                AssistChip(onClick = { sheetLang = l },
                    label = { Text("เสียง: " + PlaybackFormatting.langLabel(l)) })
            }
        }
        Row {
            Lang.entries.forEach { l ->
                TextButton(onClick = { sheetLang = l }) { Text(PlaybackFormatting.langLabel(l)) }
            }
        }
    }

    sheetLang?.let { l ->
        ModalBottomSheet(onDismissRequest = { sheetLang = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("ตั้งค่าเสียง: ${PlaybackFormatting.langLabel(l)}",
                    style = MaterialTheme.typography.titleMedium)
                VoiceLanguageEditor(lang = l)
            }
        }
    }
}
```
(Confirm Coil 3's import is `coil3.compose.AsyncImage` — match whatever `LibraryComponents.kt`/`MiniPlayer.kt` already import. Confirm `KanomTheme.colors.highlightContainer`/`onHighlight` member names against `KanomColors.kt`.)

- [ ] **Step 4: Add `onExpand` to the mini-player and wire navigation**

In `MiniPlayer.kt`, thread an `onExpand` callback and make the info area clickable (not the transport buttons):
```kotlin
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    MiniPlayerBar(
        state = state,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::next,
        onPrev = viewModel::prev,
        onSleep = { viewModel.setSleep(15) },
        onExpand = onExpand,
        modifier = modifier,
    )
}
```
Add `onExpand: () -> Unit` to `MiniPlayerBar`'s parameters and attach `Modifier.clickable(onClick = onExpand)` to the title/cover area (import `androidx.compose.foundation.clickable`). In `KanomApp.kt`, update the call inside the `Scaffold` bottomBar:
```kotlin
                MiniPlayer(
                    onExpand = {
                        navController.navigate(KanomDestination.NOW_PLAYING.route) {
                            popUpTo(KanomDestination.LIBRARY.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
```

- [ ] **Step 5: Build + full test + lint**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; tests pass; no new lint errors (2 pre-existing warnings are acceptable).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/ui/NowPlayingViewModel.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/screens/NowPlayingScreen.kt \
        app/src/main/kotlin/com/nextz/kanomtts/playback/ui/MiniPlayer.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt
git commit -m "feat(nowplaying): full Now-Playing screen + sentence card + mini-player expand"
```

**Device validation (after review):** mini-player tap opens Now-Playing; sentence card tracks speech; transport + speed/pitch chips work and apply at the next sentence; sleep chip; language switcher sheet edits the right language; idle empty state shows when nothing plays.

---

## Final integration pass (after Task 7)

- [ ] Run the whole suite: `./gradlew :text:pipeline:test :text:replacement:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — all green.
- [ ] Opus whole-branch review (subagent-driven-development's end-of-branch gate); record in `.superpowers/sdd/progress.md`.
- [ ] Device matrix: the per-task device-validation notes above, end to end on a real device.
- [ ] `finishing-a-development-branch` to merge.

---

## Self-Review (completed during authoring)

**Spec coverage:** Now-Playing screen → Task 7. Sentence card seam → Task 4 + Task 7. Live config → Task 4. Voice/speed/pitch per language + preview → Tasks 4/5. Settings = Voices + Playback + premium stub → Task 6. Quick chips → Task 7. `VoiceCatalog` seam → Task 3. Mini-player expand → Task 7. Pure helpers → Task 1. Error/empty states → editor empty-voices hint (Task 5), idle Now-Playing (Task 7). Out-of-scope (neural/word-highlight) correctly excluded.

**Plan-vs-spec correction:** spec §4.5 assumed four *new* DataStore keys; recon showed the keys + setters already exist in `PreferencesRepository` — Task 2 is reduced to an interface + fake change, no new keys. No scope change.

**Type consistency:** `PlaybackFormatting` / `VoiceSamples` / `VoiceCatalog` / `VoicePreviewer` / `VoiceConfigSupplier.observe()` / `currentSentenceText` / `VoiceLangState` / `NowPlayingUiState` names are used identically across the tasks that produce and consume them.

**Implementer must confirm on first open (noted inline):** the `FakeReaderPreferences` path/shape; the `KanomDatabase` voice-config DAO accessor name; Coil 3 `AsyncImage` import; `KanomTheme.colors` highlight member names; presence of a controller `init {}` block.
