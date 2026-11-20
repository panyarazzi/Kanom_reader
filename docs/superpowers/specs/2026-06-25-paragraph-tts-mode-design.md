# Paragraph TTS reading mode — design

Date: 2026-06-25
Status: Approved (design); pending spec review → implementation plan

## Problem

In the current (sentence) reading mode, each sentence is a separate utterance handed to the
TTS engine. Even with the `QUEUE_ADD` gapless prefetch, smoothness between sentences depends on
the engine pre-synthesising queued utterances, which is engine-dependent. Some users want
**guaranteed gapless** continuous reading and are willing to trade fine-grained tracking for it.

## Goal

Add a **new, selectable reading mode** that reads a whole paragraph as one continuous block, so a
single-language paragraph is spoken in one `speak()` call (truly gapless regardless of engine).
The current sentence mode remains the default and is unchanged.

Non-goals: word-level highlight (dropped, plan 08c); mid-paragraph page-turn in this mode;
sentence-level skip in this mode.

## Decisions (from brainstorming)

1. **Highlight + page-follow: paragraph-level.** Highlight the whole paragraph; the page turns at
   paragraph boundaries. A paragraph spanning two pages does **not** auto-turn mid-paragraph (the
   user accepts this trade-off for gapless audio). No `onRangeStart` tracking is used.
2. **New selectable mode**, default = Sentence. Sentence mode behaviour is untouched.
3. **Next/Previous skip by paragraph** in this mode (the engine cannot seek inside a one-shot
   utterance, so sentence-level skip is not meaningful here).
4. **Mixed-language paragraph:** speak each same-language run as one block. A pure-language
   paragraph → one block (fully gapless). A tiny gap remains only at each in-paragraph language
   switch (unavoidable — voices are engine-global). Highlight + skip stay paragraph-level.

## Approach (chosen): chunk at the content source

Because highlight, page-follow, and skip are all paragraph-level, the new mode is purely a
**chunking** change. The controller speak loop, highlight (`currentLocator` → decoration), page
-follow (`navigator.go`), skip (`cursor ± 1`), and the `QUEUE_ADD` same-voice prefetch are all
**unchanged**. Only the unit of a `SentenceUtterance` changes from a sentence to a paragraph.

Rejected alternative — *coalesce in the controller* (merge adjacent same-voice sentences at play
time): more moving parts in the speak loop, no benefit over source-side chunking.

## Components

### 1. Preference
- `KanomPreferences.readingUnit: ReadingUnit` where `enum class ReadingUnit { SENTENCE, PARAGRAPH }`,
  default `SENTENCE`.
- `PreferencesRepository`: persist key `reading_unit` (store as name string); setter
  `setReadingUnit(ReadingUnit)`.
- Settings UI: a toggle/selector row (Thai label, e.g. "อ่านทีละย่อหน้า" / read by paragraph).

### 2. `TextPipeline.processParagraph(input: String, maxRunChars: Int): List<SpeechSegment>`
- Splits the WHOLE paragraph into language runs (no sentence split):
  `HanDisambiguator.assign(ScriptRunSplitter.split(input), primary)`.
- For each run: `Normalizer.normalize(replacement.apply(run.text), run.lang, options).trim()`
  (same replacement + Thai-digit + mai-yamok expansion as sentence mode), dropping empty runs.
- Any run whose spoken text exceeds `maxRunChars` is split into ≤`maxRunChars` chunks, preferring a
  whitespace boundary near the limit (hard-cut fallback), each emitted as its own **same-voice**
  segment (so the engine speaks them back-to-back via `QUEUE_ADD`). This keeps chunking pure and
  unit-testable inside `text:pipeline`, with the limit injected by the caller.
- Returns the paragraph's segments in order. A pure-Thai paragraph (within the limit) → one segment.

### 3. `DefaultTtsContentSource`
- Reads `readingUnit` from prefs at the start of `stream()` (alongside `skipUrls`).
- PARAGRAPH mode: for each `Content.TextElement`, emit ONE
  `SentenceUtterance(locator = el.locator, segments = pipeline.processParagraph(el.text, MAX_RUN_CHARS))`.
  (SENTENCE mode keeps the current per-sentence emission.)
- `MAX_RUN_CHARS` is a conservative constant (e.g. 3500) defined in `DefaultTtsContentSource`, below
  Android's `getMaxSpeechInputLength()` (~4000) so `speak()` never rejects input. No `TtsProvider`
  change is needed (avoids coupling the source to the engine); sentence mode is unaffected since
  sentences are short.
- Selection-skip, streaming, and backpressure logic are unchanged (they operate on emitted
  utterances regardless of granularity).

## Data flow (paragraph mode)

```
DefaultTtsContentSource.stream (PARAGRAPH)
  → for each TextElement el:
      emit SentenceUtterance(locator = el.locator,
                             segments = processParagraph(el.text)  // 1 per language run, chunked ≤max)
  → TtsPlaybackController.runSpeakLoop  (UNCHANGED)
      → highlight = utterance.locator   → whole paragraph highlighted
      → navigator.go(locator)           → page turns to the paragraph (at paragraph boundaries)
      → speak each segment; same-voice prefetch keeps it gapless within a run AND across
        consecutive same-language paragraphs
      → cursor ± 1                      → Next/Prev skip by paragraph
```

## Behaviour / caveats (documented, accepted)

- **Pause/resume restarts the current paragraph.** The engine cannot resume mid-utterance, so on
  resume the loop re-speaks from the cursor = paragraph start. (Sentence mode re-speaks only the
  sentence.) Acceptable for this mode.
- **Mid-paragraph page-spanning:** no auto page-turn until the paragraph ends (decision 1).
- **Now-Playing card text** (`currentSentenceText`) will show the whole-paragraph spoken text and
  may be long; acceptable (display only).

## Testing

- `TextPipelineTest` / new tests: `processParagraph` — pure-Thai → 1 segment with full spoken text;
  Thai+English → ordered runs; mai-yamok (`มากๆ`→`มาก มาก`) and replacement still applied across
  the paragraph; blank/empty → empty list.
- Over-long run chunking: a run > max length → multiple ≤max same-voice segments; reconstructable to
  the original spoken text; boundaries prefer whitespace.
- `DefaultTtsContentSource` (or its fake-backed test): PARAGRAPH mode → one utterance per element
  with the element locator; SENTENCE mode unchanged.
- Existing controller/highlight/skip/prefetch tests already cover the unchanged downstream
  behaviour; no changes expected there.

## Out of scope

- `onRangeStart`-driven sentence highlight / mid-paragraph page-turn (would be a future "smooth +
  precise" mode if ever wanted).
- Any change to sentence-mode behaviour.
