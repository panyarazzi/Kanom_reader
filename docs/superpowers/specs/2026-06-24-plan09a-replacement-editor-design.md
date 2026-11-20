# Plan 09a — Replacement Editor + Add-Replacement — Design Spec

_Written 2026-06-24. Slice **09a** of the Replacement work. **09b — Visual Replacement** (show replaced text in the reader via a display-publication transform) is a separate spec that builds on this._

Parent design: `docs/superpowers/specs/2026-06-23-kanom-tts-design.md` §5.1 (Text Replacement engine + rules apply pre-synthesis and optionally in-reader).

---

## 1. Goal & the surprising starting point

**Text Replacement is already wired end-to-end for TTS — it just has no UI to author rules, so the rules table is always empty.** Recon (2026-06-24) confirmed:

- `DefaultTtsContentSource.stream()` builds `ReplacementEngine(rules.observeEffective(bookId).first())` and injects it into `TextPipeline` as the `replacement: TextTransform`; `TextPipeline.process()` applies `replacement.apply(run.text)` **per script-run before normalization**. The Aho-Corasick engine is **O(text) regardless of rule count**, so 5,000+ entries are already handled.
- Data layer is complete: `ReplacementRuleEntity(id, scope, bookId, pattern, replacement, isRegex, enabled, position)`, `ReplacementRuleDao` (`insert`/`update`/`delete`/`observeEffective(bookId)`/`observeGlobal()`/`maxPosition()`), `ReplacementRuleRepository` (`observeEffective`→domain, `observeGlobal`→entities, `add` with auto-position, `update`, `remove`), `RuleMapper.toDomain`. TSV import/export lives in `:text:replacement` (`ReplacementTsv.import/export`, tested).

**So 09a = make it usable:** a rule **editor UI** + the **Add-Replacement-from-selection** wiring. Once rules exist, the existing pre-TTS path applies them on the next playback start.

### Locked decisions (brainstorming 2026-06-24)
1. **Split:** 09a (editor + Add-Replacement) ships first; **09b Visual Replacement** separate.
2. **Editor capabilities:** core CRUD + enable/disable + literal/regex **plus per-book scope, TSV import/export, drag-reorder**. **No** shipped default Thai pack (deferred).
3. **Editor host:** a Settings row → a **dedicated editor screen** (not an inline Settings section).
4. **Apply timing:** **on next playback start** — keep the existing snapshot-at-stream-start behavior; no change to the speak path.
5. **Per-book UX:** **reader-side.** Settings editor manages **global** rules; per-book rules are created via the reader's **Add-Replacement** (scope choice, default **Global**) and managed via a **"จัดการคำแทนที่"** row in the reader settings sheet (editor scoped to the current book). No book-picker.

### Out of 09a scope
Visual Replacement (09b), default Thai pronunciation pack, live-apply of rule edits to an in-progress session.

---

## 2. Existing seams (verified by recon 2026-06-24)

- `text/replacement/`: `ReplacementRule(pattern, replacement, isRegex=false, enabled=true, order=0)`; `ReplacementEngine(rules: List<ReplacementRule>) { fun apply(text): String }` — **compiles `Regex(pattern)` for every enabled regex rule in its constructor** (invalid regex throws here); `object ReplacementTsv { fun export(List<ReplacementRule>): String; fun import(tsv: String): List<ReplacementRule> }` (header `pattern\treplacement\tisRegex\tenabled\torder`, malformed lines skipped).
- `data/db/ReplacementRuleEntity.kt`: `@Entity("replacement_rules", indices=[Index("scope"),Index("bookId")]) data class ReplacementRuleEntity(@PrimaryKey(autoGenerate=true) id: Long=0, scope: String, bookId: String?, pattern, replacement, isRegex: Boolean, enabled: Boolean, position: Int)`.
- `data/db/ReplacementRuleDao.kt`: `insert(rule): Long` (REPLACE), `update(rule)`, `delete(id)`, `observeEffective(bookId): Flow<List<Entity>>` (scope='global' OR book match, ORDER BY position), `observeGlobal(): Flow<List<Entity>>`, `maxPosition(): Int`.
- `data/repository/ReplacementRuleRepository.kt`: `observeEffective(bookId): Flow<List<ReplacementRule>>` (domain), `observeGlobal(): Flow<List<ReplacementRuleEntity>>`, `add(entity): Long` (auto-position if `position<0`), `update(entity)`, `remove(id)`. Consumed by `DefaultTtsContentSource`.
- `data/repository/RuleMapper.kt`: `toDomain(entity): ReplacementRule` only (maps `position`→`order`). **No `toEntity`.**
- Reader selection: `ReaderViewModel` registers `R.id.add_replacement` ("เพิ่มคำแทนที่") at ~line 223; handler at ~252 is `toast(R.string.toast_coming_soon)`. Selected text obtained via `stripZwsp(nav?.currentSelection()?.locator?.text?.highlight.orEmpty()).trim()`.
- UI: Settings `SettingsScreen` (Voices + Playback + premium stub, scrollable Column); `ReaderSettingsSheet` (ModalBottomSheet, `SectionLabel`/`ToggleRow`/`FilterChip`/`Slider` helpers); nav `KanomApp` NavHost + `KanomDestination`; Activity-scoped SAF pattern in `ImportViewModel`/`EpubImporter`.

---

## 3. Architecture

Packages (collapsed-module convention): `feature/replacement/` (editor VM + screen + add-sheet), small additions to `data/` (DAO/repo/mapper), `reader/` (Add-Replacement wiring), `feature/settings/` + `ui/navigation/` (the Settings row + route).

### 3.1 Navigation & entry points
- **`KanomApp` NavHost** gains two routes (plain routes, not bottom-nav tabs):
  - `replacements` → `ReplacementEditorScreen(scope = Global)`.
  - `replacements/{bookId}` → `ReplacementEditorScreen(scope = Book(bookId))`.
- **Settings**: a "คำแทนที่ · Replacements" row → `navController.navigate("replacements")`. `SettingsScreen` gains an `onOpenReplacements: () -> Unit` param (wired in `KanomApp`, mirroring Library's `onOpenBook`).
- **Reader settings sheet**: a "จัดการคำแทนที่" row → `navigate("replacements/{bookId}")` for the open book.
- **Reader selection Add-Replacement**: the stub handler becomes a **quick-add sheet** (see §3.4).

### 3.2 Editor screen (`feature/replacement/`)
**`ReplacementEditorViewModel`** (`@HiltViewModel`, `SavedStateHandle` carries the optional `bookId` → scope) injects `ReplacementRuleRepository`. State: the rule list for the scope (global → `observeGlobal()`; book → new `observeByBook(bookId)`), plus an `inheritedGlobalCount` for the book scope (count of global rules, shown as "+ N กฎทั่วไปก็มีผลด้วย"). Actions:
- `addRule(pattern, replacement, isRegex)` → **validate** (§3.5) → `repo.add(RuleMapper.toEntity(domain, scope, bookId))`.
- `updateRule(entity)`, `deleteRule(id)`, `toggleEnabled(entity)`.
- `reorder(fromIndex, toIndex)` → reassign `position` across the affected rules (pure helper) → persist via new `repo.reorder(list)`.
- `exportTsv(): String` (build domain list → `ReplacementTsv.export`), `importTsv(text)` (`ReplacementTsv.import` → entities in this scope → add).

**`ReplacementEditorScreen`**: a `LazyColumn` rule list — each row shows `pattern → replacement`, a literal/regex badge, an enable `Switch`, a drag handle, and a delete affordance; tap a row → edit sheet. A "+" FAB → add sheet. Top-bar actions: **import / export TSV**. Book scope shows the inherited-globals note. Reuses the `ReaderSettingsSheet` control vocabulary + Anchan theme. Drag-reorder via a reorderable `LazyColumn` (detect drag → `reorder(from,to)`).

**`ReplacementRuleSheet`** (add/edit, `ModalBottomSheet`): pattern field, replacement field, literal/regex toggle; inline validation error; Save/Cancel. Reused by add and edit.

### 3.3 Data / seam additions
- **`ReplacementRuleDao`**: add
  - `@Query("SELECT * FROM replacement_rules WHERE scope='book' AND bookId=:bookId ORDER BY position ASC") fun observeByBook(bookId: String): Flow<List<ReplacementRuleEntity>>`
  - `@Query("SELECT COUNT(*) FROM replacement_rules WHERE scope='global'") fun globalCount(): Flow<Int>`
  - `@Update suspend fun updateAll(rules: List<ReplacementRuleEntity>)` (reorder persistence).
- **`ReplacementRuleRepository`**: add `observeByBook(bookId): Flow<List<ReplacementRuleEntity>>`, `globalCount(): Flow<Int>`, `reorder(rules: List<ReplacementRuleEntity>)` (→ `dao.updateAll`). Keep existing methods.
- **`RuleMapper`**: add `fun toEntity(rule: ReplacementRule, scope: String, bookId: String?, id: Long = 0): ReplacementRuleEntity` (maps `order`→`position`).
- **Apply path unchanged:** `DefaultTtsContentSource` keeps `rules.observeEffective(bookId).first()` (snapshot at stream start). Documented: rule edits take effect on the next playback start.

### 3.4 Add-Replacement from selection (`reader/`)
`ReaderViewModel` injects `ReplacementRuleRepository`. The `add_replacement` menu handler reads the stripped selection and opens a **quick-add sheet** (reader-side composable) with: pattern **prefilled** from the selection, replacement field, literal/regex toggle (default literal), and a **scope choice (ทั่วไป Global [default] / เฉพาะเล่มนี้ This book)**. On save → validate → `repo.add(toEntity(domain, scope=chosen, bookId = if (book) currentBookId else null))` → toast confirm. (Selection `ActionMode` finishes inside the coroutine after the suspend `currentSelection()` read, per the existing pattern.)

### 3.5 Correctness: rule validation (pure, TDD)
`object ReplacementRuleValidation { fun validate(pattern: String, isRegex: Boolean): RuleError? }` returning `null` when valid:
- empty/blank pattern → `RuleError.EmptyPattern`.
- `isRegex && runCatching { Regex(pattern) }.isFailure` → `RuleError.InvalidRegex`.
The editor and the quick-add sheet **block save** on a non-null error and show an inline message. This prevents an invalid regex from reaching `ReplacementEngine`'s constructor (which would throw at the next stream start and **crash playback**).

### 3.6 Reorder logic (pure, TDD)
`fun reassignPositions(rules: List<ReplacementRuleEntity>, from: Int, to: Int): List<ReplacementRuleEntity>` — moves the item and rewrites `position = index` for the whole list; returns the changed entities to persist. Pure, deterministic.

### 3.7 TSV import/export (SAF, Activity-scoped)
Mirror `ImportViewModel`/`EpubImporter`: **export** → `ACTION_CREATE_DOCUMENT` (`text/tab-separated-values`) → write `exportTsv()`; **import** → `ACTION_OPEN_DOCUMENT` → read text → `importTsv()`. The editor exposes `exportTsv()`/`importTsv(text)`; the Activity-scoped piece does the SAF round-trip and hands bytes/string across. Imported rules enter the current editor scope.

---

## 4. Data flow
```
Edit rule (editor / quick-add) ─→ validate ─→ ReplacementRuleRepository.add/update/remove/reorder ─→ Room
                                                          │
Settings editor  observeGlobal() ───────────────────────►│ editor list (global)
Reader editor    observeByBook(bookId) + globalCount() ──►│ editor list (book) + "+N global" note
Next playback start ─→ DefaultTtsContentSource: ReplacementEngine(observeEffective(bookId).first()) ─→ TextPipeline (pre-synthesis)
TSV  ─ export: rules → ReplacementTsv.export → SAF write ; import: SAF read → ReplacementTsv.import → add(scope)
```

## 5. Error handling
- Invalid regex / empty pattern → inline error, save blocked (§3.5) — never reaches the engine.
- TSV import: malformed lines already skipped by `ReplacementTsv.import`; report imported/skipped counts via a toast.
- Empty rule list → editor shows an empty-state hint ("ยังไม่มีกฎ — เพิ่มกฎแรก").
- Reorder/edit are `suspend` repo writes on `viewModelScope`; no main-thread DB access.

## 6. Testing strategy
- **Pure (TDD):** `ReplacementRuleValidation.validate` (empty, valid/invalid regex, literal always-valid); `reassignPositions` (move up/down/no-op, positions contiguous); `RuleMapper.toEntity`/`toDomain` round-trip.
- **Repo (Robolectric + in-memory Room):** `observeByBook`, `globalCount`, `reorder` (positions persisted), `add` auto-position.
- **VM (Robolectric + in-memory repo, `Dispatchers.setMain(UnconfinedTestDispatcher())` per the Plan-08a pattern):** add/edit/delete/toggle write-through; reorder; import builds the right entities in scope; export emits the expected TSV.
- **Engine + TSV:** already covered in `:text:replacement`.
- **Manual device:** add a rule → it pronounces differently on next playback start; per-book rule only affects that book; Add-Replacement prefilled from a selection; drag-reorder persists; TSV export/import round-trip; invalid regex rejected (no crash).

## 7. Build sequence (hint for writing-plans)
1. **Pure helpers** (TDD): `ReplacementRuleValidation`, `reassignPositions`, `RuleMapper.toEntity`.
2. **DAO/repo additions** (`observeByBook`, `globalCount`, `updateAll`/`reorder`) + Robolectric Room tests.
3. **`ReplacementEditorViewModel`** + tests (Robolectric, in-memory repo, setMain pattern).
4. **`ReplacementEditorScreen` + `ReplacementRuleSheet`** + the `replacements` / `replacements/{bookId}` routes + the Settings "Replacements" row (+ `onOpenReplacements` wiring) + the reader settings-sheet "จัดการคำแทนที่" row.
5. **TSV import/export** SAF wiring (Activity-scoped).
6. **Reader Add-Replacement** quick-add sheet (`ReaderViewModel` + sheet, scope choice default Global) replacing the stub.
7. Opus whole-branch review; ledger in `.superpowers/sdd/progress.md`; `finishing-a-development-branch`.

## 8. Open items / risks
- **Drag-reorder library:** Compose has no built-in reorderable `LazyColumn`; either add a small reorder dependency or implement a minimal `detectDragGesturesAfterLongPress` + offset/index math. Decide in the plan (prefer a minimal hand-rolled reorder over a new dependency unless it's painful).
- **Scope of imported rules:** TSV has no scope/bookId column (engine domain only); import always lands in the current editor scope (global editor → global; book editor → that book). Documented behavior, not a bug.
- **Reorder across enabled/disabled:** `position` orders the whole scope; disabled rules keep their position (engine skips them). Fine.
- **Per-book editor inherited globals:** shown as a count note only (no inline global editing from the book editor) — keeps the screen focused; global editing stays in Settings.
