# Paragraph TTS Reading Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a selectable "read by paragraph" TTS mode that speaks a whole paragraph as one continuous (gapless) block, while the default sentence mode is unchanged.

**Architecture:** Pure chunking change at the content source. A new `readingUnit` preference selects granularity. `TextPipeline.processParagraph` turns a paragraph into one segment per language run (over-long runs chunked). A pure `TtsUtteranceBuilder` maps an element's (text, locator) to utterances for the chosen mode. `DefaultTtsContentSource` reads the pref and emits one utterance per paragraph in paragraph mode. The controller speak loop, highlight, page-follow, skip, and the existing `QUEUE_ADD` prefetch are unchanged.

**Tech Stack:** Kotlin, Jetpack DataStore (prefs), JUnit + Robolectric (app module tests), plain JUnit + icu4j (`text:pipeline` tests), Jetpack Compose (settings UI), Readium 3.3.0 (`Locator`).

## Global Constraints

- `readingUnit` default = `SENTENCE`; sentence-mode behaviour MUST NOT change.
- Paragraph mode: highlight + page-follow + Next/Prev are all paragraph-level (one utterance per paragraph). No `onRangeStart`/word tracking.
- Mixed-language paragraph: one segment per same-language run, in order.
- Over-long run cap constant `MAX_RUN_CHARS = 3500` (below Android `getMaxSpeechInputLength()` ~4000), defined in `DefaultTtsContentSource`.
- `text:pipeline` tests are plain JUnit (no Robolectric). App-module tests that build a `Locator` use `@Config(sdk = [35])` + `RobolectricTestRunner`.
- TDD, frequent commits, exact paths.

---

### Task 1: `ReadingUnit` preference

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/prefs/KanomPreferences.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/prefs/ReaderPreferences.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/prefs/PreferencesRepository.kt`
- Modify: `app/src/test/kotlin/com/nextz/kanomtts/playback/util/FakeReaderPreferences.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/prefs/ReadingUnitPrefTest.kt`

**Interfaces:**
- Produces: `enum class ReadingUnit { SENTENCE, PARAGRAPH }` (package `com.nextz.kanomtts.data.prefs`); `KanomPreferences.readingUnit: ReadingUnit` (default `SENTENCE`); `ReaderPreferences.setReadingUnit(v: ReadingUnit)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/nextz/kanomtts/data/prefs/ReadingUnitPrefTest.kt`:

```kotlin
package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.playback.util.FakeReaderPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingUnitPrefTest {
    @Test fun `defaults to SENTENCE`() = runTest {
        assertEquals(ReadingUnit.SENTENCE, FakeReaderPreferences().preferences.first().readingUnit)
    }

    @Test fun `setReadingUnit writes through the interface`() = runTest {
        val prefs: ReaderPreferences = FakeReaderPreferences()
        prefs.setReadingUnit(ReadingUnit.PARAGRAPH)
        assertEquals(ReadingUnit.PARAGRAPH, prefs.preferences.first().readingUnit)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.data.prefs.ReadingUnitPrefTest"`
Expected: FAIL — `ReadingUnit` / `readingUnit` / `setReadingUnit` unresolved.

- [ ] **Step 3: Add the enum + field**

In `KanomPreferences.kt`, add the enum next to `ReadingFont` and the field to the data class:

```kotlin
enum class ReadingUnit { SENTENCE, PARAGRAPH }
```
```kotlin
    val visualReplacement: Boolean = false,
    val readingUnit: ReadingUnit = ReadingUnit.SENTENCE,
)
```

- [ ] **Step 4: Add the interface setter**

In `ReaderPreferences.kt`, add to the interface:

```kotlin
    suspend fun setReadingUnit(v: ReadingUnit)
```

- [ ] **Step 5: Implement in the repository**

In `PreferencesRepository.kt` `Keys`, add:

```kotlin
        val readingUnit = stringPreferencesKey("reading_unit")
```
In the `preferences` map block, add (after `visualReplacement = ...`):

```kotlin
            readingUnit = p[Keys.readingUnit]?.let { runCatching { ReadingUnit.valueOf(it) }.getOrNull() } ?: DEFAULTS.readingUnit,
```
Add the setter:

```kotlin
    override suspend fun setReadingUnit(v: ReadingUnit): Unit { store.edit { it[Keys.readingUnit] = v.name } }
```

- [ ] **Step 6: Implement in the fake**

In `FakeReaderPreferences.kt`, add:

```kotlin
    override suspend fun setReadingUnit(v: ReadingUnit) { _prefs.value = _prefs.value.copy(readingUnit = v) }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.data.prefs.ReadingUnitPrefTest"`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/prefs/KanomPreferences.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/prefs/ReaderPreferences.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/prefs/PreferencesRepository.kt \
        app/src/test/kotlin/com/nextz/kanomtts/playback/util/FakeReaderPreferences.kt \
        app/src/test/kotlin/com/nextz/kanomtts/data/prefs/ReadingUnitPrefTest.kt
git commit -m "feat(prefs): add readingUnit (SENTENCE/PARAGRAPH) preference"
```

---

### Task 2: `TextPipeline.processParagraph`

**Files:**
- Modify: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/TextPipeline.kt`
- Test: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/ProcessParagraphTest.kt`

**Interfaces:**
- Consumes: existing `ScriptRunSplitter.split`, `HanDisambiguator.assign`, `Normalizer.normalize`, `SpeechSegment`.
- Produces: `TextPipeline.processParagraph(input: String, maxRunChars: Int): List<SpeechSegment>` — one segment per maximal same-language run over the WHOLE input (no sentence split), replacement + normalization + mai-yamok applied; any run whose spoken text exceeds `maxRunChars` is split into ≤`maxRunChars` same-language chunks (whitespace-preferred, hard-cut fallback).

- [ ] **Step 1: Write the failing test**

Create `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/ProcessParagraphTest.kt`:

```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessParagraphTest {
    private val pipe = TextPipeline(primary = Lang.TH)

    @Test fun `pure thai multi-sentence paragraph is one segment`() {
        val segs = pipe.processParagraph("สวัสดีชาวโลก ฉันชอบกินผัก", maxRunChars = 4000)
        assertEquals(1, segs.size)
        assertEquals(Lang.TH, segs[0].lang)
        assertTrue(segs[0].spokenText.contains("สวัสดีชาวโลก"))
        assertTrue(segs[0].spokenText.contains("ฉันชอบกินผัก"))
    }

    @Test fun `mixed language paragraph yields runs in order`() {
        val segs = pipe.processParagraph("สวัสดี Kanom จบ", maxRunChars = 4000)
        assertEquals(listOf(Lang.TH, Lang.EN, Lang.TH), segs.map { it.lang })
    }

    @Test fun `mai yamok is expanded in paragraph mode`() {
        val segs = pipe.processParagraph("ฉันชอบกินผักมากๆ", maxRunChars = 4000)
        assertEquals(1, segs.size)
        assertEquals("ฉันชอบกินผักมาก มาก", segs[0].spokenText)
    }

    @Test fun `over-long run is chunked to the limit`() {
        val long = "ก".repeat(50)
        val segs = pipe.processParagraph(long, maxRunChars = 10)
        assertTrue("expected multiple chunks", segs.size > 1)
        assertTrue("each chunk within limit", segs.all { it.spokenText.length <= 10 })
        assertEquals(long, segs.joinToString("") { it.spokenText })
    }

    @Test fun `blank input yields no segments`() {
        assertEquals(emptyList<SpeechSegment>(), pipe.processParagraph("   ", maxRunChars = 4000))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :text:pipeline:test --tests "com.nextz.kanomtts.text.pipeline.ProcessParagraphTest"`
Expected: FAIL — `processParagraph` unresolved.

- [ ] **Step 3: Implement `processParagraph` + chunking**

In `TextPipeline.kt`, add inside the class:

```kotlin
    /**
     * Like [process] but treats the WHOLE [input] as one paragraph: one [SpeechSegment] per
     * maximal same-language run (no sentence split). Replacement + normalization + mai-yamok are
     * applied per run. A run whose spoken text exceeds [maxRunChars] is split into ≤[maxRunChars]
     * same-language chunks (preferring a whitespace boundary) so the TTS engine never rejects it.
     */
    fun processParagraph(input: String, maxRunChars: Int): List<SpeechSegment> {
        val runs = HanDisambiguator.assign(ScriptRunSplitter.split(input), primary)
        return runs.flatMap { run ->
            val spoken = Normalizer.normalize(replacement.apply(run.text), run.lang, options).trim()
            if (spoken.isEmpty()) emptyList()
            else chunk(spoken, maxRunChars).map { SpeechSegment(run.lang, it) }
        }
    }

    private fun chunk(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + maxChars, text.length)
            if (end == text.length) { out += text.substring(start); break }
            val lastSpace = text.lastIndexOf(' ', end - 1)
            val cut = if (lastSpace > start) lastSpace + 1 else end  // hard-cut when no space
            out += text.substring(start, cut)
            start = cut
        }
        return out
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :text:pipeline:test --tests "com.nextz.kanomtts.text.pipeline.ProcessParagraphTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/TextPipeline.kt \
        text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/ProcessParagraphTest.kt
git commit -m "feat(pipeline): add processParagraph (per-language-run segments + chunking)"
```

---

### Task 3: `TtsUtteranceBuilder` (pure utterance mapping)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/playback/TtsUtteranceBuilder.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/playback/TtsUtteranceBuilderTest.kt`

**Interfaces:**
- Consumes: `TextPipeline.process`, `TextPipeline.processParagraph`, `SentenceLocators.forSentence`, `SentenceUtterance`, Readium `Locator`.
- Produces: `TtsUtteranceBuilder.build(text: String, locator: Locator, byParagraph: Boolean, pipeline: TextPipeline, maxRunChars: Int): List<SentenceUtterance>`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/nextz/kanomtts/playback/TtsUtteranceBuilderTest.kt`:

```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.TextPipeline
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
class TtsUtteranceBuilderTest {
    private val pipe = TextPipeline(primary = Lang.TH)
    private fun loc(text: String) = Locator(
        href = Url("ch1.xhtml")!!, mediaType = MediaType.XHTML,
        text = Locator.Text(highlight = text),
    )

    @Test fun `paragraph mode emits one utterance with the element locator`() {
        val text = "สวัสดีชาวโลก ฉันชอบกินผัก"
        val out = TtsUtteranceBuilder.build(text, loc(text), byParagraph = true, pipeline = pipe, maxRunChars = 4000)
        assertEquals(1, out.size)
        assertEquals(loc(text), out[0].locator)
        assertEquals(1, out[0].segments.size)   // pure-Thai → one run
    }

    @Test fun `sentence mode emits one utterance per sentence`() {
        val text = "สวัสดีชาวโลก ฉันชอบกินผัก"   // space between Thai clauses → 2 sentences
        val out = TtsUtteranceBuilder.build(text, loc(text), byParagraph = false, pipeline = pipe, maxRunChars = 4000)
        assertEquals(2, out.size)
    }

    @Test fun `paragraph mode with no speakable text emits nothing`() {
        val out = TtsUtteranceBuilder.build("   ", loc("   "), byParagraph = true, pipeline = pipe, maxRunChars = 4000)
        assertEquals(emptyList<SentenceUtterance>(), out)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.playback.TtsUtteranceBuilderTest"`
Expected: FAIL — `TtsUtteranceBuilder` unresolved.

- [ ] **Step 3: Implement the builder**

Create `app/src/main/kotlin/com/nextz/kanomtts/playback/TtsUtteranceBuilder.kt`:

```kotlin
package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.TextPipeline
import org.readium.r2.shared.publication.Locator

/**
 * Maps one content element's (text, locator) to the [SentenceUtterance]s to speak.
 *
 * Pure + side-effect free so it is unit-testable without a Readium publication.
 *  - byParagraph=false: one utterance per sentence (locator narrowed to the sentence range).
 *  - byParagraph=true: ONE utterance for the whole element (element locator), with one segment per
 *    same-language run — a pure-language paragraph becomes a single gapless speak.
 */
object TtsUtteranceBuilder {
    fun build(
        text: String,
        locator: Locator,
        byParagraph: Boolean,
        pipeline: TextPipeline,
        maxRunChars: Int,
    ): List<SentenceUtterance> =
        if (byParagraph) {
            val segments = pipeline.processParagraph(text, maxRunChars)
            if (segments.isEmpty()) emptyList() else listOf(SentenceUtterance(locator, segments))
        } else {
            pipeline.process(text).map { sentence ->
                SentenceUtterance(
                    locator = SentenceLocators.forSentence(locator, sentence.start, sentence.end),
                    segments = sentence.segments,
                )
            }
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.playback.TtsUtteranceBuilderTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/TtsUtteranceBuilder.kt \
        app/src/test/kotlin/com/nextz/kanomtts/playback/TtsUtteranceBuilderTest.kt
git commit -m "feat(playback): add TtsUtteranceBuilder (sentence/paragraph chunking)"
```

---

### Task 4: Wire `DefaultTtsContentSource` to the preference

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/playback/DefaultTtsContentSource.kt`

**Interfaces:**
- Consumes: `KanomPreferences.readingUnit`, `ReadingUnit`, `TtsUtteranceBuilder.build`.

- [ ] **Step 1: Add the import + constant**

In `DefaultTtsContentSource.kt`, add imports:

```kotlin
import com.nextz.kanomtts.data.prefs.ReadingUnit
```
Add to the `companion object` (next to `langOf`):

```kotlin
        /** Conservative cap below Android getMaxSpeechInputLength() (~4000) for one speak() call. */
        private const val MAX_RUN_CHARS = 3500
```

- [ ] **Step 2: Read the preference once, before the iterator loop**

In `stream { ... }`, where `skipUrls` is read via `prefs.preferences.first()`, capture the reading unit too. Replace the existing single read with one snapshot:

```kotlin
            val prefsSnapshot = prefs.preferences.first()
            val pipeline = TextPipeline(
                primary = langOf(book.lang),
                replacement = TextTransform { engine.apply(it) },
                options = NormalizerOptions(skipUrls = prefsSnapshot.skipUrls),
            )
            val byParagraph = prefsSnapshot.readingUnit == ReadingUnit.PARAGRAPH
```

(If `prefs.preferences.first()` is currently called twice/inline, consolidate to the single `prefsSnapshot` above so the pref is read once.)

- [ ] **Step 3: Replace the per-sentence emission with the builder**

Replace this block:

```kotlin
                        pipeline.process(el.text).forEach { sentence ->
                            emit(
                                SentenceUtterance(
                                    locator = SentenceLocators.forSentence(
                                        el.locator,
                                        sentence.start,
                                        sentence.end,
                                    ),
                                    segments = sentence.segments,
                                )
                            )
                        }
```
with:

```kotlin
                        TtsUtteranceBuilder.build(
                            text = el.text,
                            locator = el.locator,
                            byParagraph = byParagraph,
                            pipeline = pipeline,
                            maxRunChars = MAX_RUN_CHARS,
                        ).forEach { emit(it) }
```

- [ ] **Step 4: Verify the whole suite still passes (sentence mode unchanged)**

Run: `./gradlew :app:testDebugUnitTest :text:pipeline:test`
Expected: BUILD SUCCESSFUL (all existing tests green — sentence mode behaviour unchanged; `TtsUtteranceBuilder` already covered in Task 3).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/playback/DefaultTtsContentSource.kt
git commit -m "feat(playback): emit one utterance per paragraph in paragraph mode"
```

---

### Task 5: Settings toggle

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/screens/SettingsScreen.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModelTest.kt` (add a case)

**Interfaces:**
- Consumes: `ReaderPreferences.setReadingUnit`, `KanomPreferences.readingUnit`, `ReadingUnit`.
- Produces: `PlaybackSettingsViewModel.setReadingByParagraph(v: Boolean)`.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModelTest.kt` (import `com.nextz.kanomtts.data.prefs.ReadingUnit`, `kotlinx.coroutines.flow.first`):

```kotlin
    @Test fun `setReadingByParagraph maps to ReadingUnit`() = runTest {
        val prefs = FakeReaderPreferences()
        val vm = PlaybackSettingsViewModel(prefs)
        vm.setReadingByParagraph(true)
        advanceUntilIdle()
        assertEquals(ReadingUnit.PARAGRAPH, prefs.preferences.first().readingUnit)
        vm.setReadingByParagraph(false)
        advanceUntilIdle()
        assertEquals(ReadingUnit.SENTENCE, prefs.preferences.first().readingUnit)
    }
```

(If the existing test class has no dispatcher rule, use `kotlinx.coroutines.test.runTest` + `advanceUntilIdle`, matching the other cases in the file. If the file does not yet exist, mirror the structure of `PlaybackPrefsSettersTest`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModelTest"`
Expected: FAIL — `setReadingByParagraph` unresolved.

- [ ] **Step 3: Add the ViewModel setter**

In `PlaybackSettingsViewModel.kt`, add the import `import com.nextz.kanomtts.data.prefs.ReadingUnit` and:

```kotlin
    fun setReadingByParagraph(v: Boolean) = viewModelScope.launch {
        prefs.setReadingUnit(if (v) ReadingUnit.PARAGRAPH else ReadingUnit.SENTENCE)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Add the toggle row to Settings**

In `SettingsScreen.kt`, under the "การเล่น" section (after the highlight toggle on line 57), add:

```kotlin
        ToggleRow("อ่านทีละย่อหน้า (ต่อเนื่อง)", prefs.readingUnit == ReadingUnit.PARAGRAPH, playbackVm::setReadingByParagraph)
```
Add the import `import com.nextz.kanomtts.data.prefs.ReadingUnit` at the top of the file.

- [ ] **Step 6: Verify the app compiles + full suite passes**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :text:pipeline:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModel.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/screens/SettingsScreen.kt \
        app/src/test/kotlin/com/nextz/kanomtts/feature/settings/PlaybackSettingsViewModelTest.kt
git commit -m "feat(settings): add read-by-paragraph toggle"
```

---

## Manual / device verification (after implementation)

- Paragraph mode ON: a pure-Thai paragraph reads with no inter-sentence gap; highlight covers the paragraph; page turns at paragraph boundaries; Next/Prev jump by paragraph.
- A paragraph spanning two pages: page does NOT turn mid-paragraph (expected); turns when the next paragraph starts.
- Mixed Thai+English paragraph: reads each language run; tiny gap only at the switch.
- Pause mid-paragraph then resume: paragraph restarts from its beginning (expected limitation).
- Sentence mode (default): unchanged from today.

## Notes for the implementer

- Do NOT modify `TtsPlaybackController`, the highlight/page-follow collector in `ReaderViewModel`, the `QUEUE_ADD` prefetch, or `SystemTtsProvider`. Paragraph mode rides on them unchanged.
- Keep `text:pipeline` free of Android/Readium deps (it is a pure module). `processParagraph` uses only existing pipeline pieces.
