# Text Processing Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure-JVM text-processing core of Kanom TTS that turns a raw block of mixed-language EPUB text into an ordered list of speakable sentences — each carrying its original character range (for sentence-level highlight) and its per-language speech segments (for voice routing).

**Architecture:** Two new pure-Kotlin/JVM Gradle library modules with zero Android dependencies, so they unit-test fast and deterministically (no device, no Robolectric). `:text:pipeline` does language analysis (script splitting → Han/Kanji disambiguation → sentence tokenizing → normalization → final assembly). `:text:replacement` is a self-contained pronunciation-correction engine (literal + regex rules, TSV import/export) consumed by the pipeline. Thai/Japanese/Chinese word and sentence breaking uses **ICU4J** (`com.ibm.icu:icu4j`) bundled as a dependency rather than the Android-framework `android.icu`, so the same dictionary-based behavior runs identically in JVM unit tests and on-device.

**Tech Stack:** Kotlin (JVM), Gradle Kotlin DSL, ICU4J, JUnit4 (matches the existing version catalog).

## Global Constraints

- Package root: `com.nextz.kanomtts` (these modules: `com.nextz.kanomtts.text.pipeline`, `com.nextz.kanomtts.text.replacement`) — copied from spec §1.
- minSdk 26 / compileSdk 36 / AGP 9.2.1 for the overall project; **these two modules are pure JVM libraries** and do not apply the Android plugin — copied from spec §0 header.
- Languages handled in v1: **Thai, English, Japanese, Chinese** (`Lang.TH | EN | JA | ZH`) — copied from spec §2.
- Han (漢字/汉字) is shared between Japanese and Chinese; disambiguate by (1) per-book primary language, (2) Hiragana/Katakana presence, (3) local kana proximity, (4) user override — copied from spec §4.2.
- All offsets returned by the pipeline are **character indices into the exact input string** passed to it (so the Reader can map them to a Readium Locator). Replacement/normalization must NOT shift these offsets — they only affect the *spoken* text, never the highlight range — copied from spec §3.3.
- Highlight granularity for v1 is **sentence-level** — copied from spec §2.
- No placeholders, DRY, YAGNI, TDD, frequent commits.

---

## File Structure

```
settings.gradle.kts                         (modify: include the two modules)
gradle/libs.versions.toml                   (modify: add kotlin-jvm plugin + icu4j + kotlin-stdlib)
text/pipeline/build.gradle.kts              (create: JVM lib module)
text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/
    Lang.kt                                 (Lang enum)
    Script.kt                               (Script enum + classifier)
    ScriptRun.kt                            (ScriptRun model)
    ScriptRunSplitter.kt                    (Task 2)
    LangRun.kt                              (LangRun model)
    HanDisambiguator.kt                     (Task 3)
    Sentence.kt                             (Sentence model)
    SentenceTokenizer.kt                    (Task 4)
    Normalizer.kt                           (Task 5)
    SpeechModels.kt                         (SpeechSegment, SpokenSentence — Task 6)
    TextPipeline.kt                         (assembly — Task 6)
text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/
    ScriptRunSplitterTest.kt
    HanDisambiguatorTest.kt
    SentenceTokenizerTest.kt
    NormalizerTest.kt
    TextPipelineTest.kt
text/replacement/build.gradle.kts           (create: JVM lib module)
text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/
    ReplacementRule.kt                      (model)
    ReplacementEngine.kt                    (Task 7 — Aho-Corasick + regex)
    AhoCorasick.kt                          (Task 7 — literal multi-pattern matcher)
    ReplacementTsv.kt                       (Task 8 — import/export)
text/replacement/src/test/kotlin/com/nextz/kanomtts/text/replacement/
    AhoCorasickTest.kt
    ReplacementEngineTest.kt
    ReplacementTsvTest.kt
```

---

### Task 1: Create the two JVM library modules and wire the build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `text/pipeline/build.gradle.kts`
- Create: `text/replacement/build.gradle.kts`
- Create: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/SmokeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: two buildable modules `:text:pipeline` and `:text:replacement`; version-catalog aliases `libs.plugins.kotlin.jvm`, `libs.icu4j`, `libs.junit`.

- [ ] **Step 1: Add catalog entries**

In `gradle/libs.versions.toml`, under `[versions]` add (reconcile `kotlin` with the version the AGP 9.2.1 toolchain ships; `2.1.0` is the assumed floor — Step 6 verifies the build):
```toml
kotlin = "2.1.0"
icu4j = "76.1"
```
Under `[libraries]` add:
```toml
icu4j = { group = "com.ibm.icu", name = "icu4j", version.ref = "icu4j" }
```
Under `[plugins]` add:
```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: Register modules in settings**

In `settings.gradle.kts`, after `include(":app")` add:
```kotlin
include(":text:pipeline")
include(":text:replacement")
```

- [ ] **Step 3: Create `text/pipeline/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(libs.icu4j)
    testImplementation(libs.junit)
}
```

- [ ] **Step 4: Create `text/replacement/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    testImplementation(libs.junit)
}
```

- [ ] **Step 5: Write a smoke test**

`text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/SmokeTest.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale
import org.junit.Assert.assertTrue
import org.junit.Test

class SmokeTest {
    @Test fun icu4j_thai_word_break_is_available() {
        val bi = BreakIterator.getWordInstance(ULocale.forLanguageTag("th"))
        bi.setText("สวัสดีครับ")
        var count = 0
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) { if (end > start) count++; start = end; end = bi.next() }
        // ICU's Thai dictionary should split "สวัสดีครับ" into more than one token.
        assertTrue("expected ICU Thai dictionary breaking to produce >1 token", count > 1)
    }
}
```

- [ ] **Step 6: Run the smoke test to verify the toolchain + ICU4J**

Run: `./gradlew :text:pipeline:test --tests "com.nextz.kanomtts.text.pipeline.SmokeTest"`
Expected: PASS. If the Kotlin plugin version is rejected by AGP 9.2.1, adjust `kotlin` in the catalog to the version the toolchain reports and re-run until green. (This step is the early toolchain canary for the whole project.)

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts text/
git commit -m "build: add :text:pipeline and :text:replacement JVM modules with ICU4J"
```

---

### Task 2: Script-run splitter

Splits an input string into maximal contiguous runs of a single relevant script. Latin letters, ASCII digits, and common punctuation/whitespace are absorbed into the neighbouring run so "Kanom TTS 2024!" stays one Latin run; Thai, Han, Hiragana, Katakana each form their own runs.

**Files:**
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/Script.kt`
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/ScriptRun.kt`
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/ScriptRunSplitter.kt`
- Test: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/ScriptRunSplitterTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class Script { THAI, LATIN, HAN, HIRAGANA, KATAKANA, NEUTRAL }`
  - `data class ScriptRun(val text: String, val script: Script, val start: Int, val end: Int)` where `end` is exclusive and `text == input.substring(start, end)`.
  - `object ScriptRunSplitter { fun split(input: String): List<ScriptRun> }`
  - `fun scriptOf(codePoint: Int): Script` in `Script.kt`.

- [ ] **Step 1: Write the failing test**

`ScriptRunSplitterTest.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptRunSplitterTest {
    @Test fun pure_latin_is_one_run() {
        val runs = ScriptRunSplitter.split("Kanom TTS 2024!")
        assertEquals(1, runs.size)
        assertEquals(Script.LATIN, runs[0].script)
        assertEquals("Kanom TTS 2024!", runs[0].text)
        assertEquals(0, runs[0].start); assertEquals(15, runs[0].end)
    }

    @Test fun thai_with_embedded_english_splits_into_three_runs() {
        // "เปิดแอป Kanom แล้ว" -> [Thai][Latin][Thai]
        val runs = ScriptRunSplitter.split("เปิดแอป Kanom แล้ว")
        assertEquals(3, runs.size)
        assertEquals(Script.THAI, runs[0].script)
        assertEquals(Script.LATIN, runs[1].script)
        assertEquals("Kanom", runs[1].text.trim())
        assertEquals(Script.THAI, runs[2].script)
    }

    @Test fun offsets_reconstruct_the_input() {
        val input = "ABCひらがな漢字"
        val runs = ScriptRunSplitter.split(input)
        assertEquals(input, runs.joinToString("") { it.text })
        runs.forEach { assertEquals(it.text, input.substring(it.start, it.end)) }
    }

    @Test fun leading_space_attaches_to_following_run_not_a_neutral_run() {
        val runs = ScriptRunSplitter.split(" Hello")
        assertEquals(1, runs.size)
        assertEquals(Script.LATIN, runs[0].script)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :text:pipeline:test --tests "*ScriptRunSplitterTest"`
Expected: FAIL — unresolved references `ScriptRunSplitter`, `Script`.

- [ ] **Step 3: Implement `Script.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

enum class Script { THAI, LATIN, HAN, HIRAGANA, KATAKANA, NEUTRAL }

/** Classifies a single Unicode code point into a [Script]. Digits, punctuation,
 * whitespace and symbols are NEUTRAL and get absorbed into the surrounding run. */
fun scriptOf(codePoint: Int): Script = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.THAI -> Script.THAI
    Character.UnicodeScript.LATIN -> Script.LATIN
    Character.UnicodeScript.HAN -> Script.HAN
    Character.UnicodeScript.HIRAGANA -> Script.HIRAGANA
    Character.UnicodeScript.KATAKANA -> Script.KATAKANA
    else -> Script.NEUTRAL
}
```

- [ ] **Step 4: Implement `ScriptRun.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

data class ScriptRun(val text: String, val script: Script, val start: Int, val end: Int)
```

- [ ] **Step 5: Implement `ScriptRunSplitter.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

object ScriptRunSplitter {

    /** Splits [input] into maximal runs of a single non-neutral script.
     *  NEUTRAL code points (spaces, digits, punctuation) attach to the current
     *  open run; a leading neutral prefix attaches to the first real run. */
    fun split(input: String): List<ScriptRun> {
        if (input.isEmpty()) return emptyList()
        val runs = mutableListOf<ScriptRun>()
        var runStart = 0
        var runScript: Script? = null   // null = only neutrals seen so far in this run
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            val s = scriptOf(cp)
            if (s != Script.NEUTRAL) {
                if (runScript == null) {
                    runScript = s
                } else if (s != runScript) {
                    // close current run [runStart, i) and open a new one at i
                    runs += ScriptRun(input.substring(runStart, i), runScript, runStart, i)
                    runStart = i
                    runScript = s
                }
            }
            i += charCount
        }
        val finalScript = runScript ?: Script.NEUTRAL
        runs += ScriptRun(input.substring(runStart, input.length), finalScript, runStart, input.length)
        return runs
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :text:pipeline:test --tests "*ScriptRunSplitterTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 7: Commit**

```bash
git add text/pipeline/src
git commit -m "feat(text): script-run splitter for mixed-script text"
```

---

### Task 3: Han/Kanji disambiguator

Assigns each script run a routing `Lang` (TH/EN/JA/ZH). THAI→TH, LATIN→EN, HIRAGANA/KATAKANA→JA. HAN is ambiguous: resolve via (a) kana adjacency in the same input, then (b) the book's `primary` hint.

**Files:**
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/Lang.kt`
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/LangRun.kt`
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/HanDisambiguator.kt`
- Test: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/HanDisambiguatorTest.kt`

**Interfaces:**
- Consumes: `ScriptRun`, `Script` (Task 2).
- Produces:
  - `enum class Lang { TH, EN, JA, ZH }`
  - `data class LangRun(val text: String, val lang: Lang, val start: Int, val end: Int)`
  - `object HanDisambiguator { fun assign(runs: List<ScriptRun>, primary: Lang): List<LangRun> }`
  - Contract: `primary` is the book's declared/overridden language (defaults supplied by callers; for a Thai book pass `Lang.TH`). Adjacent runs that resolve to the same `Lang` are **merged** into one `LangRun` spanning both, so neutral-glued runs don't fragment speech.

- [ ] **Step 1: Write the failing test**

`HanDisambiguatorTest.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class HanDisambiguatorTest {
    private fun langs(input: String, primary: Lang): List<Lang> =
        HanDisambiguator.assign(ScriptRunSplitter.split(input), primary).map { it.lang }

    @Test fun han_next_to_kana_is_japanese() {
        // 日本語のテスト : Han + Hiragana + Katakana all -> JA, merged to one run
        val runs = HanDisambiguator.assign(ScriptRunSplitter.split("日本語のテスト"), Lang.ZH)
        assertEquals(listOf(Lang.JA), runs.map { it.lang })
        assertEquals(1, runs.size)
    }

    @Test fun lone_han_uses_primary_chinese() {
        assertEquals(listOf(Lang.ZH), langs("中文测试", Lang.ZH))
    }

    @Test fun lone_han_uses_primary_japanese_when_book_is_japanese() {
        assertEquals(listOf(Lang.JA), langs("漢字", Lang.JA))
    }

    @Test fun thai_latin_routing_is_independent_of_primary() {
        assertEquals(listOf(Lang.TH, Lang.EN, Lang.TH), langs("เปิดแอป Kanom แล้ว", Lang.TH))
    }

    @Test fun adjacent_same_lang_runs_merge() {
        // Hiragana run then Katakana run -> both JA -> merged
        val runs = HanDisambiguator.assign(ScriptRunSplitter.split("あいウエ"), Lang.JA)
        assertEquals(1, runs.size)
        assertEquals(Lang.JA, runs[0].lang)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :text:pipeline:test --tests "*HanDisambiguatorTest"`
Expected: FAIL — unresolved `HanDisambiguator`, `Lang`, `LangRun`.

- [ ] **Step 3: Implement `Lang.kt` and `LangRun.kt`**

`Lang.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

enum class Lang { TH, EN, JA, ZH }
```
`LangRun.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

data class LangRun(val text: String, val lang: Lang, val start: Int, val end: Int)
```

- [ ] **Step 4: Implement `HanDisambiguator.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

object HanDisambiguator {

    fun assign(runs: List<ScriptRun>, primary: Lang): List<LangRun> {
        if (runs.isEmpty()) return emptyList()
        val hasKana = runs.any { it.script == Script.HIRAGANA || it.script == Script.KATAKANA }
        // A book that mixes kana with Han is Japanese; otherwise lone Han follows the
        // primary hint (a Chinese book stays ZH, a Japanese book stays JA).
        val hanLang = when {
            hasKana -> Lang.JA
            primary == Lang.JA -> Lang.JA
            else -> Lang.ZH
        }
        val mapped = runs.map { run ->
            val lang = when (run.script) {
                Script.THAI -> Lang.TH
                Script.LATIN -> Lang.EN
                Script.HIRAGANA, Script.KATAKANA -> Lang.JA
                Script.HAN -> hanLang
                Script.NEUTRAL -> primary
            }
            LangRun(run.text, lang, run.start, run.end)
        }
        return mergeAdjacent(mapped)
    }

    private fun mergeAdjacent(runs: List<LangRun>): List<LangRun> {
        val out = mutableListOf<LangRun>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null && last.lang == r.lang && last.end == r.start) {
                out[out.size - 1] = last.copy(text = last.text + r.text, end = r.end)
            } else {
                out += r
            }
        }
        return out
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :text:pipeline:test --tests "*HanDisambiguatorTest"`
Expected: PASS (all 5 tests).

- [ ] **Step 6: Commit**

```bash
git add text/pipeline/src
git commit -m "feat(text): Han/Kanji language disambiguation with run merging"
```

---

### Task 4: Sentence tokenizer

Splits a text block into sentences with offsets, using ICU's sentence `BreakIterator` plus a Thai-aware refinement (ICU under-splits Thai, which has no sentence punctuation — split additionally on runs of whitespace and on the Thai paiyannoi `ฯ`). Empty/whitespace-only fragments are dropped but offsets of kept sentences remain absolute to the input.

**Files:**
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/Sentence.kt`
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/SentenceTokenizer.kt`
- Test: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/SentenceTokenizerTest.kt`

**Interfaces:**
- Consumes: ICU4J `BreakIterator`.
- Produces:
  - `data class Sentence(val text: String, val start: Int, val end: Int)` with `text == input.substring(start, end)`.
  - `object SentenceTokenizer { fun tokenize(input: String): List<Sentence> }`

- [ ] **Step 1: Write the failing test**

`SentenceTokenizerTest.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceTokenizerTest {
    @Test fun english_splits_on_terminators() {
        val s = SentenceTokenizer.tokenize("Hello world. How are you? I am fine!")
        assertEquals(3, s.size)
        assertEquals("Hello world.", s[0].text.trim())
        assertEquals("How are you?", s[1].text.trim())
    }

    @Test fun offsets_are_absolute_and_reconstruct() {
        val input = "First. Second."
        val s = SentenceTokenizer.tokenize(input)
        s.forEach { assertEquals(it.text, input.substring(it.start, it.end)) }
    }

    @Test fun thai_clauses_separated_by_space_become_separate_sentences() {
        // Thai uses spaces between clauses; expect a split on the space.
        val input = "เธอเปิดแอป แล้วกดเล่น"
        val s = SentenceTokenizer.tokenize(input)
        assertEquals(2, s.size)
        assertEquals("เธอเปิดแอป", s[0].text.trim())
        assertEquals("แล้วกดเล่น", s[1].text.trim())
    }

    @Test fun blank_input_yields_no_sentences() {
        assertTrue(SentenceTokenizer.tokenize("   \n  ").isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :text:pipeline:test --tests "*SentenceTokenizerTest"`
Expected: FAIL — unresolved `SentenceTokenizer`, `Sentence`.

- [ ] **Step 3: Implement `Sentence.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

data class Sentence(val text: String, val start: Int, val end: Int)
```

- [ ] **Step 4: Implement `SentenceTokenizer.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.util.ULocale

object SentenceTokenizer {

    private const val THAI_PAIYANNOI = 'ฯ' // ฯ

    /** Returns non-blank sentences with absolute offsets into [input]. */
    fun tokenize(input: String): List<Sentence> {
        if (input.isBlank()) return emptyList()
        val coarse = icuSentences(input)
        val refined = mutableListOf<Sentence>()
        for (c in coarse) refined += refineThai(input, c.start, c.end)
        return refined.filter { input.substring(it.start, it.end).isNotBlank() }
    }

    private fun icuSentences(input: String): List<Sentence> {
        val bi = BreakIterator.getSentenceInstance(ULocale.forLanguageTag("th"))
        bi.setText(input)
        val out = mutableListOf<Sentence>()
        var start = bi.first()
        var end = bi.next()
        while (end != BreakIterator.DONE) {
            out += Sentence(input.substring(start, end), start, end)
            start = end
            end = bi.next()
        }
        return out
    }

    /** ICU under-splits Thai (no sentence punctuation). Within an ICU sentence,
     *  also break after 'ฯ' and at a whitespace gap that sits *between two Thai
     *  characters* — Thai uses spaces to separate clauses, but spaces around an
     *  embedded English word (e.g. "เปิดแอป Kanom แล้ว") must NOT split the
     *  sentence. Offsets stay absolute. */
    private fun refineThai(input: String, from: Int, to: Int): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var segStart = from
        var i = from
        while (i < to) {
            val ch = input[i]
            val isBreak = when {
                ch == THAI_PAIYANNOI -> true
                ch.isWhitespace() -> {
                    val next = if (i + 1 < to && !input[i + 1].isWhitespace()) input[i + 1] else null
                    val prev = prevNonSpace(input, i - 1, segStart)
                    next != null && prev != null && isThai(prev) && isThai(next)
                }
                else -> false
            }
            if (isBreak) {
                val end = i + 1
                out += Sentence(input.substring(segStart, end), segStart, end)
                segStart = end
            }
            i++
        }
        if (segStart < to) out += Sentence(input.substring(segStart, to), segStart, to)
        return out
    }

    private fun prevNonSpace(input: String, fromIndex: Int, lowerBound: Int): Char? {
        var j = fromIndex
        while (j >= lowerBound) {
            if (!input[j].isWhitespace()) return input[j]
            j--
        }
        return null
    }

    private fun isThai(c: Char): Boolean = scriptOf(c.code) == Script.THAI
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :text:pipeline:test --tests "*SentenceTokenizerTest"`
Expected: PASS (all 4 tests). If ICU's English sentence count differs, the refinement does not merge — fix only if a test fails; the Thai whitespace split is the load-bearing behavior.

- [ ] **Step 6: Commit**

```bash
git add text/pipeline/src
git commit -m "feat(text): Thai-aware sentence tokenizer over ICU BreakIterator"
```

---

### Task 5: Normalizer

Per-language text normalization applied to the *spoken* text only. v1 scope: Thai digits → Arabic digits (so the system TTS reads them), a small abbreviation map, and an optional "skip URLs" pass that replaces URLs with a spoken placeholder. Pure function; never used to compute highlight offsets.

**Files:**
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/Normalizer.kt`
- Test: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/NormalizerTest.kt`

**Interfaces:**
- Consumes: `Lang` (Task 3).
- Produces:
  - `data class NormalizerOptions(val skipUrls: Boolean = false, val urlPlaceholder: String = "ลิงก์")`
  - `object Normalizer { fun normalize(text: String, lang: Lang, options: NormalizerOptions = NormalizerOptions()): String }`

- [ ] **Step 1: Write the failing test**

`NormalizerTest.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    @Test fun thai_digits_become_arabic() {
        assertEquals("123", Normalizer.normalize("๑๒๓", Lang.TH))
    }

    @Test fun urls_replaced_when_skip_enabled() {
        val out = Normalizer.normalize(
            "ดูที่ https://example.com/path นะ", Lang.TH,
            NormalizerOptions(skipUrls = true)
        )
        assertEquals("ดูที่ ลิงก์ นะ", out)
    }

    @Test fun urls_kept_when_skip_disabled() {
        val input = "see https://example.com now"
        assertEquals(input, Normalizer.normalize(input, Lang.EN))
    }

    @Test fun english_abbreviation_expanded() {
        assertEquals("et cetera", Normalizer.normalize("etc.", Lang.EN))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :text:pipeline:test --tests "*NormalizerTest"`
Expected: FAIL — unresolved `Normalizer`, `NormalizerOptions`.

- [ ] **Step 3: Implement `Normalizer.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

data class NormalizerOptions(val skipUrls: Boolean = false, val urlPlaceholder: String = "ลิงก์")

object Normalizer {
    private val URL = Regex("""https?://\S+""")
    private val THAI_DIGIT_BASE = '๐'.code // ๐ == 0
    private val EN_ABBR = mapOf("etc." to "et cetera", "e.g." to "for example", "i.e." to "that is")

    fun normalize(text: String, lang: Lang, options: NormalizerOptions = NormalizerOptions()): String {
        var out = text
        if (options.skipUrls) out = URL.replace(out, options.urlPlaceholder)
        out = thaiDigitsToArabic(out)
        if (lang == Lang.EN) for ((k, v) in EN_ABBR) out = out.replace(k, v)
        return out
    }

    private fun thaiDigitsToArabic(s: String): String {
        if (s.none { it in '๐'..'๙' }) return s
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '๐'..'๙') ('0' + (ch.code - THAI_DIGIT_BASE)) else ch)
        return sb.toString()
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :text:pipeline:test --tests "*NormalizerTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 5: Commit**

```bash
git add text/pipeline/src
git commit -m "feat(text): per-language normalizer (Thai digits, URLs, abbreviations)"
```

---

### Task 6: Pipeline assembly

Combines Tasks 2–5 plus the replacement engine (Task 7) into the public API. For each sentence, splits into language runs, applies replacement then normalization to each run's spoken text, and emits a `SpokenSentence` carrying the original range (for highlight) and ordered `SpeechSegment`s (for voice routing). Empty segments after transformation are dropped, but the sentence's original range is preserved.

**Files:**
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/SpeechModels.kt`
- Create: `text/pipeline/src/main/kotlin/com/nextz/kanomtts/text/pipeline/TextPipeline.kt`
- Test: `text/pipeline/src/test/kotlin/com/nextz/kanomtts/text/pipeline/TextPipelineTest.kt`
- Modify: `text/pipeline/build.gradle.kts` (add `implementation(project(":text:replacement"))`)

**Interfaces:**
- Consumes: `ScriptRunSplitter`, `HanDisambiguator`, `SentenceTokenizer`, `Normalizer`, and `com.nextz.kanomtts.text.replacement.ReplacementEngine` (Task 7) via the abstraction below.
- Produces:
  - `data class SpeechSegment(val lang: Lang, val spokenText: String)`
  - `data class SpokenSentence(val start: Int, val end: Int, val originalText: String, val segments: List<SpeechSegment>)`
  - `fun interface TextTransform { fun apply(text: String): String }` — replacement engine adapts to this; pass `TextTransform { it }` for no-op.
  - `class TextPipeline(private val primary: Lang, private val replacement: TextTransform = TextTransform { it }, private val options: NormalizerOptions = NormalizerOptions()) { fun process(input: String): List<SpokenSentence> }`

- [ ] **Step 1: Add the module dependency**

In `text/pipeline/build.gradle.kts` `dependencies { }` add:
```kotlin
implementation(project(":text:replacement"))
```
(Replacement is consumed via the `TextTransform` interface, so the pipeline does not hard-depend on the engine's concrete type — but the module link lets callers wire it.)

- [ ] **Step 2: Write the failing test**

`TextPipelineTest.kt`:
```kotlin
package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPipelineTest {
    @Test fun mixed_sentence_routes_to_three_segments() {
        val p = TextPipeline(primary = Lang.TH)
        val out = p.process("เปิดแอป Kanom แล้ว")
        assertEquals(1, out.size)
        val seg = out[0].segments
        assertEquals(listOf(Lang.TH, Lang.EN, Lang.TH), seg.map { it.lang })
        assertEquals("Kanom", seg[1].spokenText.trim())
    }

    @Test fun original_range_maps_back_to_input_for_highlight() {
        val input = "First. Second."
        val out = TextPipeline(primary = Lang.EN).process(input)
        assertEquals(2, out.size)
        out.forEach { assertEquals(it.originalText, input.substring(it.start, it.end)) }
    }

    @Test fun replacement_changes_spoken_text_but_not_offsets() {
        val input = "อ่าน GPT ดู"
        val transform = TextTransform { it.replace("GPT", "จีพีที") }
        val out = TextPipeline(primary = Lang.TH, replacement = transform).process(input)
        // highlight range still covers the original input substring
        assertEquals(input, input.substring(out[0].start, out[0].end))
        // but GPT is spoken as the replacement
        assertTrue(out[0].segments.any { it.spokenText.contains("จีพีที") })
    }

    @Test fun thai_digits_normalized_in_spoken_text() {
        val out = TextPipeline(primary = Lang.TH).process("หน้า ๑๒")
        assertTrue(out.flatMap { it.segments }.any { it.spokenText.contains("12") })
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :text:pipeline:test --tests "*TextPipelineTest"`
Expected: FAIL — unresolved `TextPipeline`, `SpokenSentence`, `SpeechSegment`, `TextTransform`.

- [ ] **Step 4: Implement `SpeechModels.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

data class SpeechSegment(val lang: Lang, val spokenText: String)

data class SpokenSentence(
    val start: Int,
    val end: Int,
    val originalText: String,
    val segments: List<SpeechSegment>,
)

fun interface TextTransform { fun apply(text: String): String }
```

- [ ] **Step 5: Implement `TextPipeline.kt`**

```kotlin
package com.nextz.kanomtts.text.pipeline

class TextPipeline(
    private val primary: Lang,
    private val replacement: TextTransform = TextTransform { it },
    private val options: NormalizerOptions = NormalizerOptions(),
) {
    fun process(input: String): List<SpokenSentence> {
        val sentences = SentenceTokenizer.tokenize(input)
        return sentences.mapNotNull { sentence ->
            val runs = HanDisambiguator.assign(ScriptRunSplitter.split(sentence.text), primary)
            val segments = runs.mapNotNull { run ->
                val spoken = Normalizer.normalize(replacement.apply(run.text), run.lang, options).trim()
                if (spoken.isEmpty()) null else SpeechSegment(run.lang, spoken)
            }
            if (segments.isEmpty()) null
            else SpokenSentence(sentence.start, sentence.end, sentence.text, segments)
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :text:pipeline:test --tests "*TextPipelineTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 7: Run the whole pipeline module's tests**

Run: `./gradlew :text:pipeline:test`
Expected: PASS (all suites from Tasks 1–6).

- [ ] **Step 8: Commit**

```bash
git add text/pipeline
git commit -m "feat(text): assemble TextPipeline (segments + highlight ranges)"
```

---

### Task 7: Replacement engine (Aho-Corasick literals + regex)

The pronunciation-correction core in `:text:replacement`. Applies an ordered rule list to text: literal rules matched efficiently in one pass via Aho-Corasick (handles thousands of rules), regex rules applied in order. Earlier rules win on overlap. This is the `TextTransform` the pipeline consumes.

**Files:**
- Create: `text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementRule.kt`
- Create: `text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/AhoCorasick.kt`
- Create: `text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementEngine.kt`
- Test: `text/replacement/src/test/kotlin/com/nextz/kanomtts/text/replacement/AhoCorasickTest.kt`
- Test: `text/replacement/src/test/kotlin/com/nextz/kanomtts/text/replacement/ReplacementEngineTest.kt`

**Interfaces:**
- Consumes: nothing (no module deps).
- Produces:
  - `data class ReplacementRule(val pattern: String, val replacement: String, val isRegex: Boolean = false, val enabled: Boolean = true, val order: Int = 0)`
  - `class AhoCorasick(literals: List<String>)` with `fun firstMatchAt(text: String, index: Int): Int` returning the length of the longest keyword matching at `index`, or `-1`.
  - `class ReplacementEngine(rules: List<ReplacementRule>)` with `fun apply(text: String): String`. Construction sorts enabled rules by `order`, builds the literal automaton once, and pre-compiles regexes. Enabled literal rules are applied in a single left-to-right scan (longest match at each position, earliest `order` breaks ties); then regex rules apply in `order` sequence.

- [ ] **Step 1: Write the failing Aho-Corasick test**

`AhoCorasickTest.kt`:
```kotlin
package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class AhoCorasickTest {
    @Test fun finds_longest_keyword_at_position() {
        val ac = AhoCorasick(listOf("ab", "abc"))
        assertEquals(3, ac.firstMatchAt("abcd", 0)) // longest = "abc"
    }

    @Test fun no_match_returns_minus_one() {
        val ac = AhoCorasick(listOf("xyz"))
        assertEquals(-1, ac.firstMatchAt("abc", 0))
    }

    @Test fun matches_thai_keyword() {
        val ac = AhoCorasick(listOf("กรุงเทพ"))
        assertEquals("กรุงเทพ".length, ac.firstMatchAt("ไป กรุงเทพ", 3))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :text:replacement:test --tests "*AhoCorasickTest"`
Expected: FAIL — unresolved `AhoCorasick`.

- [ ] **Step 3: Implement `ReplacementRule.kt`**

```kotlin
package com.nextz.kanomtts.text.replacement

data class ReplacementRule(
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val enabled: Boolean = true,
    val order: Int = 0,
)
```

- [ ] **Step 4: Implement `AhoCorasick.kt`**

```kotlin
package com.nextz.kanomtts.text.replacement

/** Trie-based longest-match lookup at a given position. (Goto + output is enough
 *  for our "match at index" query; full failure links aren't needed because the
 *  engine advances the cursor past each match itself.) */
class AhoCorasick(literals: List<String>) {
    private class Node {
        val next = HashMap<Char, Node>()
        var wordLen = -1 // length of a keyword ending here, or -1
    }
    private val root = Node()

    init {
        for (w in literals) if (w.isNotEmpty()) {
            var n = root
            for (c in w) n = n.next.getOrPut(c) { Node() }
            if (n.wordLen < w.length) n.wordLen = w.length
        }
    }

    /** Longest keyword length matching starting exactly at [index], else -1. */
    fun firstMatchAt(text: String, index: Int): Int {
        var n = root
        var i = index
        var best = -1
        while (i < text.length) {
            n = n.next[text[i]] ?: break
            if (n.wordLen > best) best = n.wordLen
            i++
        }
        return best
    }
}
```

- [ ] **Step 5: Run to verify Aho-Corasick passes**

Run: `./gradlew :text:replacement:test --tests "*AhoCorasickTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 6: Write the failing engine test**

`ReplacementEngineTest.kt`:
```kotlin
package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementEngineTest {
    @Test fun literal_replacement_applied() {
        val e = ReplacementEngine(listOf(ReplacementRule("GPT", "จีพีที")))
        assertEquals("อ่าน จีพีที ดู", e.apply("อ่าน GPT ดู"))
    }

    @Test fun longest_literal_wins_at_a_position() {
        val e = ReplacementEngine(listOf(
            ReplacementRule("AI", "เอไอ", order = 1),
            ReplacementRule("AIX", "เอไอเอกซ์", order = 2),
        ))
        assertEquals("เอไอเอกซ์", e.apply("AIX"))
    }

    @Test fun disabled_rule_is_skipped() {
        val e = ReplacementEngine(listOf(ReplacementRule("X", "Y", enabled = false)))
        assertEquals("X", e.apply("X"))
    }

    @Test fun regex_rule_applied_after_literals() {
        val e = ReplacementEngine(listOf(
            ReplacementRule("""\d+""", "[เลข]", isRegex = true)
        ))
        assertEquals("หน้า [เลข]", e.apply("หน้า 123"))
    }

    @Test fun no_rules_is_identity() {
        assertEquals("hello", ReplacementEngine(emptyList()).apply("hello"))
    }
}
```

- [ ] **Step 7: Run to verify it fails**

Run: `./gradlew :text:replacement:test --tests "*ReplacementEngineTest"`
Expected: FAIL — unresolved `ReplacementEngine`.

- [ ] **Step 8: Implement `ReplacementEngine.kt`**

```kotlin
package com.nextz.kanomtts.text.replacement

class ReplacementEngine(rules: List<ReplacementRule>) {

    private val literalRules: List<ReplacementRule> =
        rules.filter { it.enabled && !it.isRegex }.sortedBy { it.order }
    private val regexRules: List<Pair<Regex, String>> =
        rules.filter { it.enabled && it.isRegex }.sortedBy { it.order }
            .map { Regex(it.pattern) to it.replacement }

    // pattern -> replacement (first/earliest order wins for equal-length patterns)
    private val replacementOf: Map<String, String> =
        literalRules.associate { it.pattern to it.replacement }
    private val automaton = AhoCorasick(literalRules.map { it.pattern })

    fun apply(text: String): String {
        val afterLiterals = applyLiterals(text)
        var out = afterLiterals
        for ((rx, repl) in regexRules) out = rx.replace(out, repl)
        return out
    }

    private fun applyLiterals(text: String): String {
        if (literalRules.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val len = automaton.firstMatchAt(text, i)
            if (len > 0) {
                val matched = text.substring(i, i + len)
                sb.append(replacementOf[matched] ?: matched)
                i += len
            } else {
                sb.append(text[i]); i++
            }
        }
        return sb.toString()
    }
}
```

- [ ] **Step 9: Run to verify the engine passes**

Run: `./gradlew :text:replacement:test --tests "*ReplacementEngineTest"`
Expected: PASS (all 5 tests).

- [ ] **Step 10: Commit**

```bash
git add text/replacement/src
git commit -m "feat(text): replacement engine (Aho-Corasick literals + regex rules)"
```

---

### Task 8: Replacement TSV import/export

Round-trippable TSV backup/sharing for rules (spec: "TSV export/import"). Columns: `pattern\treplacement\tisRegex\tenabled\torder`. Header row required; malformed lines skipped.

**Files:**
- Create: `text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementTsv.kt`
- Test: `text/replacement/src/test/kotlin/com/nextz/kanomtts/text/replacement/ReplacementTsvTest.kt`

**Interfaces:**
- Consumes: `ReplacementRule` (Task 7).
- Produces:
  - `object ReplacementTsv { fun export(rules: List<ReplacementRule>): String; fun import(tsv: String): List<ReplacementRule> }`
  - Contract: `import(export(x)) == x` for any list with no tab/newline characters in fields.

- [ ] **Step 1: Write the failing test**

`ReplacementTsvTest.kt`:
```kotlin
package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementTsvTest {
    @Test fun round_trips() {
        val rules = listOf(
            ReplacementRule("GPT", "จีพีที", isRegex = false, enabled = true, order = 1),
            ReplacementRule("""\d+""", "[เลข]", isRegex = true, enabled = false, order = 2),
        )
        assertEquals(rules, ReplacementTsv.import(ReplacementTsv.export(rules)))
    }

    @Test fun header_only_imports_empty() {
        assertEquals(emptyList<ReplacementRule>(), ReplacementTsv.import("pattern\treplacement\tisRegex\tenabled\torder"))
    }

    @Test fun malformed_lines_are_skipped() {
        val tsv = "pattern\treplacement\tisRegex\tenabled\torder\nGPT\tจีพีที\tfalse\ttrue\t1\nBAD_LINE"
        assertEquals(1, ReplacementTsv.import(tsv).size)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :text:replacement:test --tests "*ReplacementTsvTest"`
Expected: FAIL — unresolved `ReplacementTsv`.

- [ ] **Step 3: Implement `ReplacementTsv.kt`**

```kotlin
package com.nextz.kanomtts.text.replacement

object ReplacementTsv {
    private const val HEADER = "pattern\treplacement\tisRegex\tenabled\torder"

    fun export(rules: List<ReplacementRule>): String =
        buildString {
            append(HEADER)
            for (r in rules) {
                append('\n')
                append(r.pattern).append('\t')
                append(r.replacement).append('\t')
                append(r.isRegex).append('\t')
                append(r.enabled).append('\t')
                append(r.order)
            }
        }

    fun import(tsv: String): List<ReplacementRule> {
        val lines = tsv.split('\n')
        if (lines.isEmpty()) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val c = line.split('\t')
            if (c.size != 5) return@mapNotNull null
            val isRegex = c[2].toBooleanStrictOrNull() ?: return@mapNotNull null
            val enabled = c[3].toBooleanStrictOrNull() ?: return@mapNotNull null
            val order = c[4].toIntOrNull() ?: return@mapNotNull null
            ReplacementRule(c[0], c[1], isRegex, enabled, order)
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :text:replacement:test --tests "*ReplacementTsvTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 5: Run both modules' full test suites**

Run: `./gradlew :text:pipeline:test :text:replacement:test`
Expected: PASS (everything from Tasks 1–8).

- [ ] **Step 6: Commit**

```bash
git add text/replacement
git commit -m "feat(text): TSV import/export for replacement rules"
```

---

## Self-Review

**1. Spec coverage** (against design spec §3.3, §4.2, plus the replacement-engine and segmentation requirements this plan owns):
- Script-run splitting (spec §3.3 pipeline) → Task 2. ✓
- Han/Kanji JA-vs-ZH disambiguation (spec §4.2) → Task 3. ✓
- Thai-aware sentence tokenizing (spec §3.3, §8 spike #2) → Task 4. ✓
- Normalizer: Thai digits, abbreviations, Skip-URLs (spec §5.1) → Task 5. ✓
- Offsets preserved for sentence highlight, replacement affects spoken text only (spec §3.3 constraint) → Task 6 (`replacement_changes_spoken_text_but_not_offsets`). ✓
- Per-language voice-routing segments (spec §4.2) → Task 6 (`SpeechSegment.lang`). ✓
- Replacement engine: literal + regex, ordered, enable/disable, scales to thousands (spec §5.1 "5,000+ rules", Aho-Corasick) → Task 7. ✓
- TSV import/export (spec §5.1) → Task 8. ✓
- *Out of scope for this plan (correctly deferred):* the shipped default Thai pronunciation pack (ships as data in the Settings/Repository plan), word-level highlight (Phase 2), and Readium/TTS wiring (Plans 06–07). These consume this module but aren't part of it.

**2. Placeholder scan:** No TBD/TODO/"add error handling"/"similar to Task N". Every code step shows complete code; every test step shows real assertions. ✓

**3. Type consistency:** `Lang {TH,EN,JA,ZH}`, `Script {THAI,LATIN,HAN,HIRAGANA,KATAKANA,NEUTRAL}`, `ScriptRun`, `LangRun`, `Sentence`, `SpeechSegment`, `SpokenSentence`, `TextTransform`, `ReplacementRule`, `AhoCorasick.firstMatchAt`, `ReplacementEngine.apply`, `ReplacementTsv.export/import` are defined once and referenced with identical signatures across tasks. `TextPipeline` consumes replacement via `TextTransform` (an adapter), so the cross-module call site is `ReplacementEngine::apply` wrapped as `TextTransform { engine.apply(it) }` at the eventual DI wiring site (Plan 03/07), not inside this module. ✓

**Note for executors:** `firstMatchAt` returns `0` only if an empty keyword sneaks in; Task 7's automaton skips empty patterns, and `applyLiterals` treats `len > 0` as a match, so empty patterns can never cause an infinite loop. This invariant is relied upon — do not "simplify" the `len > 0` guard to `len >= 0`.
