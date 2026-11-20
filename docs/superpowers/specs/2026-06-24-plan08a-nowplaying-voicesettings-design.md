# Plan 08a — Now-Playing Screen + Per-Language Voice Settings — Design Spec

_Written 2026-06-24. Slice **08a** of the original Plan 08 (the rest — neural sherpa-onnx premium provider = 08b; word-level highlight = 08c — are deferred to their own spec→plan cycles)._

Parent design: `docs/superpowers/specs/2026-06-23-kanom-tts-design.md` (§4.3 per-language controls, §6.3 Now-Playing signature screen). Builds entirely on seams shipped by Plan 07 (`docs/superpowers/plans/2026-06-24-tts-playback.md`).

---

## 1. Goal & scope

Turn the two placeholder bottom-nav tabs (`กำลังฟัง` / `ตั้งค่า`) into real screens that expose the playback engine Plan 07 already built:

- **Now-Playing screen** — the mini-player expands into it; large cover, the signature **"currently speaking" sentence card**, full transport, book-progress, and quick speed/pitch/sleep/voice chips.
- **Per-language voice/speed/pitch settings** — read/write `VoiceConfigEntity` per language (TH/EN/JA/ZH), enumerate system voices, **audition** a voice before committing. Changes apply **live, at the next sentence**.
- **Settings tab** — host the Voices editors plus a small Playback section (the four `KanomPreferences` fields that have no UI yet) and a premium/about coming-soon stub.

### Out of scope (08a)
- Neural / sherpa-onnx premium provider and Play Billing premium unlock → **08b** (shown here only as a "เร็วๆ นี้" stub).
- Word-level highlight via `onRangeStart` → **08c**.
- Per-book Han (JA-vs-ZH) language override UI (parent spec §4.2) — not in this slice.

---

## 2. Decisions locked in brainstorming (2026-06-24)

1. **Slice = 08a only** (Now-Playing + voice settings UI). Neural provider and word-highlight split out.
2. **Live "currently speaking" sentence card** — add a `currentSentenceText` seam to the controller (chosen over deferring the card). The card is the screen's signature element.
3. **Voice/speed/pitch apply LIVE** — change the controller's config load from a one-shot snapshot to a live observation of `VoiceConfigRepository`, so edits take effect at the next segment of an in-progress session (chosen over apply-on-next-start).
4. **Voice preview = pause-then-sample** — auditioning a voice pauses any active playback, speaks a fixed per-language sample with the chosen voice/speed/pitch, and stays paused (chosen over deferring preview).
5. **Now-Playing surface = the existing `กำลังฟัง` bottom-nav tab** (not a new full-screen pushed route); the mini-player tap navigates to it.
6. **Settings scope = Voices + Playback section + premium/about stub** (chosen over Voices-only).
7. **Now-Playing speed/pitch = compact quick-adjust chips** for the current language; full per-language editing lives in Settings and the language-switcher bottom sheet (chosen over full sliders on the player).

---

## 3. Existing seams this builds on (verified by recon 2026-06-24)

**Playback controller** — `playback/TtsPlaybackController.kt` (`@Singleton`, all state on `@PlaybackScope` `Dispatchers.Main`):
- `state: StateFlow<PlaybackState>` — `Idle | Loading | Playing(bookId, locator) | Paused(bookId, locator) | Error(message)`.
- `currentLocator: StateFlow<Locator?>` (book progress via `locations.totalProgression`), `currentLang: StateFlow<Lang?>`, `sleepRemaining: StateFlow<Long?>`, `sleepFade: StateFlow<Float>`, `providerError: SharedFlow<String>`.
- Transport: `play()`, `pause()`, `stop()`, `nextSentence()`, `previousSentence()`, `jumpToSegment(i)`, `setSleepTimer(durationMin: Int?, endOfChapter: Boolean)`.
- **Private today** (to be exposed/changed): `cursor` index, `utterances: List<SentenceUtterance>`, `configs: Map<String, VoiceConfigEntity>` snapshot loaded once at `start()` via `voiceConfig.load()`.
- Book metadata is **not** in controller state — fetched separately via `BookReader.get(bookId)` (`BookEntity(id, uri, title, author, lang, coverPath, fileSize, addedAt, lastOpenedAt)`).

**Voice layer** — `playback/`:
- `TtsProvider` interface: `suspend fun voices(lang: Lang): List<Voice>`, `suspend fun speak(text, lang, voiceId, speedRate, pitch)`, `stop()`, `shutdown()`, `val events: Flow<TtsEvent>`.
- `SystemTtsProvider` (`@Singleton`): `voices(lang)` filters `tts.voices` by locale → `Voice(id=name, name, lang, needsNetwork, quality)`; per-`speak` it calls `setVoice`/`setLanguage`, `setSpeechRate`, `setPitch` (raw float multipliers).
- `VoiceRouter` (object, pure): `choose(lang: Lang, configs: Map<String, VoiceConfigEntity>): VoiceChoice`; `DEFAULT_SPEED=1.0f`, `DEFAULT_PITCH=1.0f`. `VoiceChoice(voiceId, speed, pitch)`.
- `VoiceConfigEntity(@PrimaryKey lang: String, providerId, voiceId: String?, speed: Float, pitch: Float)` — one row per `Lang.name`. `VoiceConfigRepository`: `observeAll(): Flow<List<VoiceConfigEntity>>`, `suspend get(lang)`, `suspend set(config)` (upsert). **No default seeding** — absent row ⇒ `VoiceRouter` defaults.
- `Lang { TH, EN, JA, ZH }` (in `:text:pipeline`); `Lang.toLocale()` lives in `SystemTtsProvider` (th-TH / en-US / ja-JP / zh-CN).

**UI scaffold** — `ui/navigation/`:
- `KanomDestination { LIBRARY("library"), NOW_PLAYING("now_playing","กำลังฟัง"), SETTINGS("settings","ตั้งค่า") }`; `KanomApp.kt` `NavHost` routes each + `reader/{bookId}`; bottom bar hidden on `reader/*`.
- `playback/ui/MiniPlayer.kt` + `PlaybackViewModel` (`MiniPlayerUiState(visible, isPlaying, title, author, coverPath, voiceLabel, sleepRemainingMs)` via `combine(state, currentLang, sleepRemaining)`). Mini-player has **no tap/expand handler today**.
- Placeholders: `ui/screens/NowPlayingScreen.kt`, `ui/screens/SettingsScreen.kt` (centered text, no VM).
- Reusable UI patterns in `reader/ReaderSettingsSheet.kt`: `FilterChip` (selection), `Slider` (ranges), `Switch`/`ToggleRow`, `SectionLabel`, `ModalBottomSheet`. Design system: `ui/theme/` — `Anchan #5D63D6`, `RoseHighlight #FF5E9C` (**reserved for TTS highlight**), `CoconutCream`, Itim (display) / Sarabun (body), `KanomShapes`, `KanomTheme.colors`. Coil 3.3.0 for covers.

**Prefs** — `data/prefs/`: `KanomPreferences` already has `skipUrls`, `autoScroll`, `highlightOn`, `sleepTimerDefaultMin`, `premiumUnlocked` fields, but `ReaderPreferences` exposes **no setters** for the first four (they have no UI home today).

---

## 4. Architecture

Module placement follows the collapsed-module decision (packages inside `:app`):
- `playback/` — controller seam changes + `VoiceCatalog`.
- `playback/ui/nowplaying/` — `NowPlayingScreen` + `NowPlayingViewModel`.
- `feature/settings/` — `SettingsScreen` + `VoiceSettingsViewModel` + the shared `VoiceLanguageEditor`.
- Pure helpers in a small `playback/ui/format` (or `feature/settings`) package, JVM-unit-tested.

### 4.1 Controller seam changes (`playback/TtsPlaybackController.kt`)

1. **`currentSentenceText: StateFlow<String?>`** — a new public `StateFlow`. The speak loop sets it to the current `SentenceUtterance`'s joined segment text each time the cursor advances; reset to `null` on `stop()`/idle. Lives on the `@PlaybackScope` main scope like the other state.

2. **Live voice config** — replace the one-shot `configs = voiceConfig.load()` at `start()`/`startAndPlay()` with a long-lived collector launched on the playback scope:
   - On first `start`, take the initial snapshot (`observeAll().first()`) so segment #1 is configured, then keep collecting `observeAll()` into the cached `configs` map.
   - `VoiceRouter.choose(segment.lang, configs)` (already per-segment in `runSpeakLoop`) then picks up edits at the **next** segment. No change to the hot path beyond reading the now-mutable map.
   - Cancel the collector on `shutdown()`.
   - **Seam:** the `VoiceConfigSupplier` injected today exposes `load()`; extend it (or its binding) with `observe(): Flow<Map<String, VoiceConfigEntity>>` backed by `repo.observeAll()` mapped by `Lang.name`, so the controller depends on the seam, not the repo directly.

3. **`suspend fun previewVoice(lang: Lang, voiceId: String?, speed: Float, pitch: Float)`** — if `state` is `Playing`, `pause()` first; then `provider.speak(sampleFor(lang), lang, voiceId, speed, pitch)` once and return (stays paused). Runs on the controller's single-threaded main scope, so it cannot interleave with the speak loop; the loop re-applies per-segment voice/rate/pitch on the next `play()`, so preview leaves no residue. Sample strings: a short fixed phrase per language (e.g. TH "สวัสดีค่ะ นี่คือตัวอย่างเสียงอ่าน").

### 4.2 Voice catalog seam (`playback/VoiceCatalog.kt`)

```kotlin
interface VoiceCatalog { suspend fun voices(lang: Lang): List<Voice> }
```
Hilt-bound to `SystemTtsProvider` (which already implements `voices`). `VoiceSettingsViewModel` depends on this narrow seam (fakeable in tests) rather than the whole provider.

### 4.3 Pure logic (TDD first — haiku-friendly)

All deterministic, JVM-unit-tested before any UI:
- **Speed/pitch clamp + display:** clamp speed to `0.5f..3.0f`, pitch to `0.5f..2.0f`; format `×%.1f` (or `×%.2f` for fine steps). Slider steps decided here.
- **Sleep `mm:ss` formatter:** `formatRemaining(ms: Long): String`.
- **`Lang.label(): String`** → ไทย / EN / 日本 / 中文 (matching the parent spec's switcher labels).
- **`progressFraction(locator: Locator?): Float`** from `locations.totalProgression`, coerced `0f..1f`.
- **Effective-config resolver:** `effectiveConfig(entity: VoiceConfigEntity?): VoiceChoice`-shaped value mirroring `VoiceRouter` defaults (so the UI shows the same numbers the engine will use).
- **Voice-list ordering:** sort by `quality` desc, stable by name; expose `needsNetwork` for a badge.

### 4.4 Now-Playing (`playback/ui/nowplaying/`)

**`NowPlayingViewModel` (`@HiltViewModel`)** — injects `TtsPlaybackController`, `BookReader`, `VoiceConfigRepository`. `combine(state, currentLocator, currentLang, currentSentenceText, sleepRemaining, sleepFade)` (+ book lookup by `bookId`) → `NowPlayingUiState`:
```
data class NowPlayingUiState(
  val present: Boolean,            // false → empty state
  val isPlaying: Boolean,
  val title: String, val author: String?, val coverPath: String?,
  val sentenceText: String?,       // the currently-speaking card
  val langLabel: String?,          // current segment language
  val progress: Float,             // book fraction 0..1
  val sleepRemainingMs: Long?, val sleepFade: Float,
  val speedDisplay: String, val pitchDisplay: String,   // current-language effective values
)
```
Actions delegate to the controller: `playPause`, `next`, `prev`, `stop`, `setSleep`, `nudgeSpeed(+/-)`, `nudgePitch(+/-)` (write `VoiceConfigRepository.set` for `currentLang`), `openLanguageSheet(lang)`.

**`NowPlayingScreen`** replaces the placeholder:
- Large Coil cover (rounded `KanomShapes.large`), Itim title + Sarabun author.
- **"Currently speaking" sentence card** — `RoseHighlight`-tinted surface (`KanomTheme.colors.highlightContainer` / `onHighlight`), the reserved rose so it always reads as "this is being spoken"; gentle cross-fade between sentences (respect reduce-motion).
- Transport row: prev / play-pause (M3 Expressive shape-morph) / next / stop.
- Book-progress bar from `progress`.
- Chips row: speed quick-adjust, pitch quick-adjust (compact step chips for the current language), sleep-timer chip (reuse mini-player `SleepChip` styling), and the **language switcher** (ไทย/EN/日本/中文) opening the per-language `VoiceLanguageEditor` in a `ModalBottomSheet`.
- **Idle/empty state** when `!present`: "ยังไม่มีการเล่น" card.

**Mini-player wiring:** add an `onExpand` tap handler to `MiniPlayer`/`MiniPlayerBar` that navigates to `KanomDestination.NOW_PLAYING` (passed down from `KanomApp`).

### 4.5 Voice settings (`feature/settings/`)

**`VoiceSettingsViewModel` (`@HiltViewModel`)** — injects `VoiceCatalog`, `VoiceConfigRepository`, `TtsPlaybackController` (for `previewVoice`). State per language: available `List<Voice>` (lazy-loaded on expand), current `VoiceConfigEntity` (or defaults), preview-in-progress flag. Actions: `selectVoice(lang, voiceId)`, `setSpeed(lang, v)`, `setPitch(lang, v)` (each `repo.set(...)`), `preview(lang)`.

**`VoiceLanguageEditor` (the reusable unit)** — for one `Lang`: voice picker (dropdown/menu or selectable list — voice lists can be long; network voices badged), speed `Slider` (`0.5f..3.0f`), pitch `Slider` (`0.5f..2.0f`), and a **"เล่นตัวอย่าง" (play sample)** button → `preview(lang)`. Built from the `ReaderSettingsSheet` control vocabulary. Reused verbatim in (a) the Settings Voices section and (b) the Now-Playing language-switcher sheet.

**`SettingsScreen`** replaces its placeholder:
- **Voices** section — a language selector (chips TH/EN/JA/ZH) revealing the `VoiceLanguageEditor` for the chosen language (keeps the screen compact vs. four stacked editors).
- **Playback** section — `Switch` rows for Skip URLs / auto-scroll-follow / sentence-highlight on-off, and a default-sleep-timer `Slider`. Requires four new `ReaderPreferences` setters + DataStore keys (`setSkipUrls`, `setAutoScroll`, `setHighlightOn`, `setSleepTimerDefaultMin`).
- **About / Premium** stub — a row "Neural · พรีเมียม — เร็วๆ นี้" (coming-soon toast/disabled, consistent with the selection-menu stubs); real unlock is 08b.

### 4.6 Data flow

```
Settings/NP edit ─→ VoiceConfigRepository.set(entity)
                      │
                      ├─→ observeAll() Flow ─→ controller live collector ─→ cached configs map
                      │                              └─ VoiceRouter.choose @ next segment (LIVE apply)
                      └─→ observeAll() Flow ─→ Settings/NP VM (display refresh)

Now-Playing UI  ←── combine(controller state flows) + BookReader  (sentence card ← currentSentenceText)
Preview         ──→ VM ──→ controller.previewVoice ──→ provider.speak(sample) once (pauses if playing)
```

### 4.7 Error handling
- **No voices for a language** (e.g. Thai not installed): editor shows "ไม่พบเสียง — ติดตั้งเสียงในระบบ"; the existing `providerError` / `LANG_MISSING_DATA` path surfaces install prompts (device item, deferred). Empty list never crashes; selection falls back to locale default (`voiceId = null`).
- **Preview when TTS not ready** (`!ready`): button disabled / no-op.
- **Idle Now-Playing**: empty state, all transport hidden; no `bookId` access on `Idle`.
- **Out-of-range slider values**: clamped by the pure helpers before `repo.set`.

---

## 5. Testing strategy
- **Unit (TDD, pure):** clamp/format/label/progressFraction/effective-config/voice-ordering helpers; `VoiceLanguageEditor` state reducers if extracted.
- **VM (Robolectric or pure with fakes):** `VoiceSettingsViewModel` against a fake `VoiceCatalog` + in-memory `VoiceConfigRepository` (select/speed/pitch write-through; preview pauses playing); `NowPlayingViewModel` state derivation from fake controller flows (idle vs playing, sentence card, progress).
- **Controller:** unit/Robolectric for the live-config collector (edit mid-session ⇒ next-segment choice changes) and `previewVoice` (pauses when playing, stays paused). Reuse Plan 07's controller test harness if present.
- **Manual device matrix** (the real validation — platform edges are device-only): sentence-card sync to speech, live speed/pitch change mid-session, preview pausing active playback and not corrupting resume, per-language voice switching, idle states, mini-player→Now-Playing expand, Thai-voice-missing path.

---

## 6. Build sequence (hint for writing-plans)
1. **Pure helpers** (TDD, haiku) — clamp/format/label/progress/effective-config/ordering.
2. **Prefs setters** (small) — four `ReaderPreferences` + DataStore keys.
3. **`VoiceCatalog` seam + binding** (small).
4. **Controller seam changes** (sonnet, integration) — `currentSentenceText`, live-config collector, `previewVoice`. Two-stage review (threading-sensitive — touches the singleton hot loop).
5. **`VoiceSettingsViewModel` + `VoiceLanguageEditor`** (sonnet) + tests.
6. **`SettingsScreen`** (Voices + Playback + stub) (sonnet).
7. **`NowPlayingViewModel` + `NowPlayingScreen`** (sonnet) + mini-player expand wiring.
8. **Opus whole-branch review**; ledger in `.superpowers/sdd/progress.md`; `finishing-a-development-branch` to merge.

Each task: fresh implementer, two-stage review; device-found fixes via root-cause → focused fix agent → re-review (the established cadence).

---

## 7. Open items / risks
- **Sentence-card text accuracy:** `currentSentenceText` must reflect the *spoken* (un-transformed) text, not the ZWSP display text — source it from the `SentenceUtterance` segments, which already come from the un-transformed pub. Verify no ZWSP leaks into the card.
- **Live-config threading:** the new collector mutates the cached `configs` map read by `runSpeakLoop` — both on the `@PlaybackScope` main dispatcher, so no lock needed, but the implementer must keep all access on that scope (no background reads).
- **Voice list size / latency:** `tts.voices` can be large and slow on first call; load per-language lazily on editor expand, not upfront.
- **Preview UX:** stays paused after sampling (no auto-resume) — confirm this matches expectation on device; revisit auto-resume if it feels abrupt.
- **Carry-forward debt from Plan 07** (per `kanom-tts-roadmap.md`) is untouched here; the `currentSentenceText` work may make the per-sentence cursor a natural place to also fix the stop-flush debounce later (note, don't scope into 08a).
