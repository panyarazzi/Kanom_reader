# Plan 09b — Visual Replacement — Design Spec

_Written 2026-06-25. Slice **09b** of the Replacement work, building on **09a** (Replacement Editor + Add-Replacement, merged to master `8684e6f`)._

Parent design: `docs/superpowers/specs/2026-06-23-kanom-tts-design.md` §5.1 ("rules apply pre-synthesis and **optionally in-reader**").

---

## 1. Goal & the two tensions recon surfaced

**Goal:** render replaced text in the reader (not just feed it to TTS), so text-correction rules (typos / OCR errors / name standardization) show up on the page.

Recon (2026-06-25) found two tensions that shape the design:

1. **It conflicts with the signature karaoke highlight.** Readium locates the rose sentence-highlight by **matching the spoken sentence's text against the rendered DOM** (the highlight `Locator.text.highlight` is a quoted substring; ZWSP injection survives only because it's invisible and character-preserving). Replacing *visible* display text breaks that match — the highlight (carrying the original text) no longer matches the replaced DOM, so it misplaces/disappears. **Resolution (locked):** when Visual Replacement is on, re-derive the highlight's quote through the same replacement so it equals the displayed text → it matches again. This is the device-validation crux.

2. **Most rules are pronunciation respellings, which look wrong on the page.** Replacement is mostly "how TTS *says* it." Rendering those visually would garble the book. **Resolution (locked):** a **per-rule `showInReader` flag** — only flagged rules render; pronunciation rules stay spoken-only.

### Locked decisions (brainstorming 2026-06-25)
- **Per-rule `showInReader: Boolean`**, default **OFF** (new rules are spoken-only until opted in; existing 09a rules default OFF via the migration).
- **Master `visualReplacement` reader pref**, default **OFF** (opt-in; the page is unchanged until enabled).
- **Keep the highlight working** by re-deriving its quote from the `showInReader`-replaced text (not "highlight pauses when on").
- **The spoken text is unchanged** — TTS still applies **all** effective rules via the existing `TextPipeline`; only the *display* (and the highlight quote) get the `showInReader` subset.

### Out of scope
Changing the spoken text; neural provider (08b); word-level highlight (08c).

---

## 2. Existing seams (verified by recon 2026-06-25)

- **Display transform seam:** `PublicationProvider.openForDisplay(uri, thaiWordBreak): Publication?` wraps the EPUB container in a `TransformingContainer` whose `transformer: (Url, Resource) -> Resource` runs `ThaiZwspInjector.inject(html)` on `.xhtml`/`.html` resources at parse time. `ThaiZwspInjector.inject` uses Jsoup `NodeTraversor` over `TextNode`s, skipping `script`/`style`, transforming `node.wholeText` → `node.text(...)`. This is the seam to also apply replacement.
- **Engine:** `com.nextz.kanomtts.text.replacement.ReplacementEngine(rules: List<ReplacementRule>).apply(text: String): String` — pure, works on any string; filters `enabled && !isRegex` (Aho-Corasick) then enabled regex in `order`. **It does not look at any `showInReader` field** (it just applies the rules it's handed).
- **Rules data:** `ReplacementRuleEntity(id, scope, bookId, pattern, replacement, isRegex, enabled, position)`; domain `ReplacementRule(pattern, replacement, isRegex, enabled, order)`; `RuleMapper.toDomain`/`toEntity`; `ReplacementTsv.import/export` (header `pattern\treplacement\tisRegex\tenabled\torder`); `ReplacementRuleRepository.observeEffective(bookId): Flow<List<ReplacementRule>>` (global + book, domain). `ReplacementRuleRepository` is **already injected into `ReaderViewModel`** (09a).
- **Load path:** `ReaderViewModel.load()` reads `prefs.preferences.first()` then calls `source.openForDisplay(book.uri, initialPrefs.thaiWordBreak)`. `ReaderBookSource.openForDisplay(uri, thaiWordBreak)` → `PublicationProvider.openForDisplay`. Resume locator restored after open.
- **Highlight path:** `DefaultTtsContentSource` (un-transformed `open()` pub) builds `SentenceUtterance.locator = SentenceLocators.forSentence(el.locator, start, end)` where `forSentence` = `element.copy(text = element.text.substring(start until end))` — so `text.highlight` is the **original** sentence substring. `ReaderDecorationController.applyTts(locator)` applies a rose `Decoration.Style.Highlight` at that locator; Readium matches it onto the display DOM by text.
- **Prefs:** `KanomPreferences` + `ReaderPreferences` (interface) + `PreferencesRepository` (DataStore `Keys` + Flow mapping + setters) — the exact pattern Plan 08a used to add `setSkipUrls` etc. `ReaderSettingsSheet` `ToggleRow` pattern (`thaiWordBreak`, `pageAnimation`); `ReaderViewModel.onThaiWordBreak(...)`.
- **Room:** `KanomDatabase @Database(version = 1)`; `DataModule` builds it with plain `.build()` (no migrations). The roadmap flags that the first schema change needs an explicit `Migration`.

---

## 3. Architecture

### 3.1 Data model: the `showInReader` flag
- **Domain** `ReplacementRule` (`:text:replacement`) gains `val showInReader: Boolean = false`. The **engine ignores it** (it stays a passthrough data field); callers filter on it. Adding it to the domain lets TSV round-trip it.
- **Entity** `ReplacementRuleEntity` gains `val showInReader: Boolean` (column `showInReader`).
- **Room v1→v2 migration:** bump `@Database(version = 2)`; add `MIGRATION_1_2 = "ALTER TABLE replacement_rules ADD COLUMN showInReader INTEGER NOT NULL DEFAULT 0"`; `DataModule` adds `.addMigrations(MIGRATION_1_2)`. (Existing rules → `showInReader = false`.)
- **`RuleMapper`** maps `showInReader` both ways (`toDomain`, `toEntity`).
- **`ReplacementTsv`** gains a trailing `showInReader` column (`pattern\treplacement\tisRegex\tenabled\torder\tshowInReader`); import tolerant of the old 5-column format (missing column → `false`).
- **New repo method:** `ReplacementRuleRepository.observeVisualEffective(bookId): Flow<List<ReplacementRule>>` = effective rules **filtered `showInReader == true`**, mapped to domain (via a DAO query `… AND showInReader = 1` or a `.map { it.filter { r -> r.showInReader } }`). This is what the display transform + highlight consume.

### 3.2 Editor + Add-Replacement get the flag
- `ReplacementRuleSheet` (the add/edit sheet) gains a **"แสดงในหน้าอ่าน (Show in reader)"** `Switch` bound to `showInReader` (default off for new rules). `ReplacementEditorViewModel.addRule`/`updateRule` carry it.
- The reader **Add-Replacement** quick-add sheet gains the same toggle (default off), threaded through `ReaderViewModel.addReplacement(..., showInReader)`.
- The editor rule row shows a small "👁" badge when `showInReader` is on.

### 3.3 The master toggle (reader pref)
- `KanomPreferences` gains `val visualReplacement: Boolean = false`; `ReaderPreferences` gains `suspend fun setVisualReplacement(v: Boolean)`; `PreferencesRepository` gains the DataStore key + Flow mapping + setter (the 08a pattern).
- `ReaderSettingsSheet` gets a `ToggleRow("แทนที่ในหน้าอ่าน (Visual Replacement)", prefs.visualReplacement, onVisualReplacement)`; `ReaderViewModel.onVisualReplacement(v) = viewModelScope.launch { prefs.setVisualReplacement(v) }`.

### 3.4 Display transform: `ReplacementInjector`
- New `reader/engine/ReplacementInjector` (mirrors `ThaiZwspInjector`): `fun inject(html: String, engine: ReplacementEngine): String` — Jsoup `NodeTraversor` over `TextNode`s (skip `script`/`style`/blank), `node.text(engine.apply(node.wholeText))` when changed.
- In `PublicationProvider.openForDisplay`, extend the signature to `openForDisplay(uri, thaiWordBreak, visualRules: List<ReplacementRule>?)`. In the `transformer` lambda for `.xhtml`/`.html`, compose **replacement THEN ZWSP**: `var s = bytes.decodeToString(); if (!visualRules.isNullOrEmpty()) s = ReplacementInjector.inject(s, ReplacementEngine(visualRules)); if (thaiWordBreak) s = ThaiZwspInjector.inject(s); s.encodeToByteArray()`. (When `visualRules` is null/empty, behaviour is identical to today.)
- Thread through `ReaderBookSource.openForDisplay(uri, thaiWordBreak, visualRules)`.
- `ReaderViewModel.load()`: after reading prefs, snapshot `val visualRules = if (prefs.visualReplacement) replacementRuleRepository.observeVisualEffective(bookId).first() else emptyList()`; pass to `openForDisplay`.

### 3.5 Highlight stays in sync (the crux)
When `visualReplacement` is on, the highlight's quote must equal the displayed (replaced) text. Approach: hold the `showInReader` `ReplacementEngine` (or its rules) + the `visualReplacement` flag in `ReaderViewModel`, and when applying the TTS highlight, transform the quote:
- `ReaderDecorationController.applyTts(locator)` (or the call site in `ReaderViewModel` that feeds it) gains an optional `visualEngine: ReplacementEngine?`. When non-null, it applies the engine to `locator.text.highlight` before building the `Decoration`: `locator.copy(text = locator.text.copy(highlight = visualEngine.apply(locator.text.highlight)))`.
- The engine is the **same `showInReader` subset** used by the display transform, so the replaced quote == the replaced DOM text (ZWSP-tolerant matching is unchanged from today, since the quote stays ZWSP-free like today and the DOM has ZWSP either way).
- Provided to the controller at bind time (`ReaderViewModel` builds it from the snapshot + flag).

### 3.6 Reload on change
`openForDisplay` runs at load, so toggling `visualReplacement` — or editing a `showInReader` rule while reading — must **reload the display publication** to re-apply, the same way `thaiWordBreak` already triggers a reload. The reload re-snapshots `observeVisualEffective` and rebuilds the highlight engine. Resume locator is preserved (existing behaviour).

---

## 4. Data flow
```
Editor / Add-Replacement (showInReader toggle) ─→ repo.add/update (entity.showInReader)
                                                      │
Reader load (visualReplacement ON): snapshot observeVisualEffective(bookId).first()
   ├─→ openForDisplay(uri, thaiWordBreak, visualRules) → ReplacementInjector(engine) then ThaiZwspInjector  → DISPLAY pub shows replaced text
   └─→ ReaderDecorationController.applyTts(locator, visualEngine) → highlight quote = engine.apply(original) → matches replaced DOM
TTS spoken path (unchanged): DefaultTtsContentSource open() + TextPipeline(all effective rules) → speech
Toggle visualReplacement / edit showInReader rule → reader reloads display pub (resume preserved)
```

## 5. Error handling
- `visualReplacement` OFF or no `showInReader` rules → `visualRules` empty → `openForDisplay` behaves exactly as today (no transform), highlight engine null (no quote change). Zero behaviour change for users who don't opt in.
- Regex in a `showInReader` rule still validated before save (09a) — the display engine compiles `Regex` too, so a bad pattern can't be saved.
- Migration is additive with a default → no data loss; existing rules become `showInReader=false`.
- Highlight under replacement is best-effort (Readium matching is a black box); if a quote fails to match on device, the highlight is dropped for that sentence (no crash) — flagged for device validation.

## 6. Testing strategy
- **Pure (TDD):** `ReplacementInjector.inject` (replaces text-node text, skips script/style, idempotent, composes with engine); the highlight-quote transform (`apply showInReader engine to a quote`); `showInReader` round-trips through `RuleMapper` + `ReplacementTsv` (incl. tolerant import of old 5-column TSV); the engine ignores `showInReader`.
- **Room (Robolectric):** `MIGRATION_1_2` (open v1 schema, migrate, assert column + default); `observeVisualEffective` returns only `showInReader` effective rules.
- **VM (Robolectric + directExecutor, the 09a pattern):** load snapshots visual rules only when `visualReplacement` on; toggle triggers reload.
- **Device (the real validation):** **highlight lands on the replaced sentence** when Visual Replacement is on (the crux); replaced text renders for `showInReader` rules only; pronunciation (non-flagged) rules don't alter the page; toggle reloads and preserves resume; composition with ZWSP word-break looks right.

## 7. Build sequence (hint for writing-plans)
1. **`showInReader` data model** (TDD): domain field + engine-ignores test; entity field + Room v1→v2 migration + DataModule; `RuleMapper`; `ReplacementTsv` column + tolerant import; `observeVisualEffective`.
2. **Editor + Add-Replacement toggle** (the showInReader Switch in the sheet + quick-add + VM plumbing + row badge).
3. **`visualReplacement` pref** (KanomPreferences + ReaderPreferences + DataStore + reader settings toggle + VM setter).
4. **`ReplacementInjector` + openForDisplay threading** (transform compose replacement→ZWSP; `ReaderBookSource`/`PublicationProvider` signature; `ReaderViewModel.load` snapshot; reload-on-toggle).
5. **Highlight quote transform** (`ReaderDecorationController` + the `visualEngine` from `ReaderViewModel`).
6. Opus whole-branch review; ledger; `finishing-a-development-branch` (device sign-off gate).

## 8. Open items / risks
- **Highlight under replacement is the primary risk** — Readium's decoration text-matching is a black box; regex rules and large length changes may shift the per-sentence match. Mitigation: derive the quote from the same engine as the display; accept best-effort (drop unmatched highlight, no crash); validate on device. If it proves unreliable for regex rules, a fallback is to apply the highlight-quote transform only for literal `showInReader` rules.
- **Composition order** (replacement before ZWSP) means ZWSP word-breaks are computed on replaced text — correct, but confirm Thai word-break still looks right on replaced Thai text on device.
- **Reload cost** on toggle (re-parses the publication) — acceptable (same as `thaiWordBreak`); only on an explicit toggle/edit, not per-frame.
- **TSV back-compat:** old 5-column exports import with `showInReader=false` (tolerant parse) — documented, not a bug.
