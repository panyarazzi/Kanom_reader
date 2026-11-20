# Plan 07 — TTS Playback · Design Spec

**Date:** 2026-06-24
**Status:** Approved (brainstorm complete) — pending spec review
**Package:** `com.nextz.kanomtts` · minSdk 26 · targetSdk/compileSdk 36 · AGP 9.2.1 · Readium 3.3.0
**Predecessors:** Plans 01–06 (DONE & merged). Builds the app's signature feature — reading the book aloud — on top of the Plan 06 reader and the Plan 01–04 text pipeline / data layer.

---

## 1. Scope

### In scope
- A **custom TTS playback engine** that reads reading-order EPUB content aloud with **in-paragraph script routing** (Thai + English + Japanese + Chinese), driven by the existing `TextPipeline` (normalizer + replacement-rule pronunciation engine + script-run split + Han JA-vs-ZH disambiguation + sentence tokenizer).
- **Sentence-level karaoke highlight** + **auto-scroll-follow**, reusing the Plan 06 Decoration machinery (the `DecorableNavigator.applyDecorations` path proven by `SentenceHighlightProbe`).
- **Background playback** via **media3**: a `MediaSessionService` + `SimpleBasePlayer` adapter → media notification, lock-screen controls, headset/Bluetooth, audio focus.
- A **persistent docked mini-player** (shown app-wide whenever playback is active).
- A **sleep timer** (fixed durations + end-of-chapter + fade-out).
- **System TTS only**, behind a `TtsProvider` seam; per-language voice/speed/pitch read from the existing `VoiceConfig` (defaults when unset).
- **Start anywhere:** a play control starts from the current position, **and long-pressing a paragraph starts TTS from that paragraph** (long-press, not tap — single tap stays bound to Plan 06's page-turn/chrome 3-zone model).

### Out of scope (→ Plan 08)
The full **Now-Playing screen**, the per-language voice/speed/pitch **settings UI**, the **replacement-rule editor**, and the **premium neural** (sherpa-onnx) provider. **Word-level** highlight stays Phase 2.

---

## 2. Architecture

New code lives under `com.nextz.kanomtts.tts` (packages in `:app`, per the locked module-collapse decision).

```
PublicationProvider.open(uri)            UN-transformed publication (NO Thai ZWSP) — the TTS path
  → publication.content() (reading order, streamed per resource/element — not whole book up-front)
  → TextPipeline.process(elementText): List<SpokenSentence>   each = [TH|EN|JA|ZH segments] + source offsets
  → TtsPlaybackController (@Singleton, source of truth)
        speak loop: per sentence → per segment → TtsProvider.speak(text, routed Voice, speed, pitch)
        state: StateFlow<PlaybackState>;  currentLocator: StateFlow<Locator?>
        transport: start / play / pause / stop / nextSentence / previousSentence
        sleep timer · audio focus · voice routing (VoiceConfig per Lang)
  ├─→ reader (ReaderDecorationController) observes currentLocator → applyDecorations(group="tts", Highlight)
  │                                              + navigator.go(locator) when autoScroll  [Plan 06 reuse]
  ├─→ mini-player (Compose) observes state
  └─→ KanomTtsPlayer : SimpleBasePlayer (wraps the controller)
        ↕ MediaSession ↕ KanomPlaybackService : MediaSessionService (foreground) → media notification / lock screen
```

**The controller is the single source of truth** — a Hilt `@Singleton` that survives across screens so playback continues in the background. In-app UI (mini-player, reader) calls the controller directly. External controls (lock-screen, notification, headset, Android Auto later) route through the `MediaSession` → `SimpleBasePlayer` → the **same** controller. Both paths converge; the controller's `StateFlow`s drive both the UI and the player's published state.

### Package layout
```
com.nextz.kanomtts.tts
  TtsProvider.kt            seam: speak one (single-language) segment + events + voices
  SystemTtsProvider.kt      android.speech.tts.TextToSpeech impl
  Voice.kt                  domain voice model (id, name, lang, …)
  TtsEvent.kt               Started / Done / Error / RangeStart
  TtsPlaybackController.kt  the engine (singleton)
  PlaybackState.kt          Idle / Loading / Playing(sentence) / Paused / Error
  SleepTimer.kt             durations + end-of-chapter + fade-out
  VoiceRouter.kt            Lang → Voice/speed/pitch (VoiceConfig + defaults), pure-ish
  SentenceLocators.kt       SpokenSentence(+element locator) → Readium Locator (pure, reuses probe pattern)
  service/
    KanomPlaybackService.kt MediaSessionService (foreground)
    KanomTtsPlayer.kt       SimpleBasePlayer wrapping the controller
  ui/
    MiniPlayer.kt           docked Compose mini-player
  di/TtsModule.kt           Hilt bindings (provider, controller singleton)
reader/
  ReaderDecorationController.kt   NEW — owns the navigator's decoration groups (bookmarks + tts + probe)
```

---

## 3. `TtsProvider` + `SystemTtsProvider`

```kotlin
interface TtsProvider {
    suspend fun voices(lang: Lang): List<Voice>
    suspend fun speak(text: String, voice: Voice?, speedRate: Float, pitch: Float)   // suspends until Done/Error
    fun stop()
    val events: Flow<TtsEvent>            // Started, Done, Error(reason), optional RangeStart(start,end)
}
data class Voice(val id: String, val name: String, val lang: Lang, val needsNetwork: Boolean = false)
```
- **`SystemTtsProvider`** wraps `android.speech.tts.TextToSpeech`: async init (gate `speak` until ready), per-locale `Voice` selection from `tts.voices`, `UtteranceProgressListener` (`onStart`/`onDone`/`onError`/`onRangeStart`) bridged to `events`, `setSpeechRate`/`setPitch`, `synthesize` per segment with a unique utteranceId. `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` → a `TtsEvent.Error(MissingVoice(lang))` the controller surfaces (and falls back: keep the previous voice or skip-with-log). The user-facing "install voice" prompt is Plan 08; Plan 07 only surfaces/falls back.
- `speak` is **per segment** (one language/voice) — the controller orchestrates sentences→segments and the cross-language switching.
- **`NeuralTtsProvider` (Plan 08)** implements the same seam → drops in without touching the controller.
- **Sentence-level highlight does NOT require `onRangeStart`** — the controller moves the highlight when it begins a sentence's first segment (it owns the loop). `onRangeStart` is only needed for word-level (Phase 2). This de-risks spec §8's #1 worry.

---

## 4. `TtsPlaybackController` (the engine)

A Hilt `@Singleton`. Owns its **own un-transformed `Publication`** for the currently-playing book (`PublicationProvider.open(book.uri)`) — separate from the reader's display publication (which is ZWSP-transformed; mixing would pollute spoken text). Closes it on stop / book-change.

**Content streaming:** iterates reading-order content **per resource/element** (not materializing the whole book) — pairs each `Content.TextElement` with its `Locator`, runs `element.text` through `TextPipeline.process()` → `List<SpokenSentence>`, and maintains a cursor `(elementIndex, sentenceIndex)`. *(Exact streaming primitive — `Content.elements()` vs the `Content` iterator vs `content(startLocator)` — is an API-recon item for writing-plans; default to chunking per reading-order resource.)*

**State / outputs:**
```kotlin
val state: StateFlow<PlaybackState>      // Idle | Loading | Playing(book, sentence) | Paused(book, sentence) | Error(msg)
val currentLocator: StateFlow<Locator?>  // current sentence's Locator (for highlight + auto-scroll + progress)
```

**Transport:** `start(bookId, fromLocator: Locator?)`, `play()`, `pause()`, `stop()`, `nextSentence()`, `previousSentence()`.

**Speak loop** (a coroutine on the controller's scope): for the current `SpokenSentence`, emit its `Locator` (highlight moves), then for each `SpeechSegment` route to a `Voice` via `VoiceRouter` and `provider.speak(segment.spokenText, voice, speed, pitch)` (await Done); between segments/sentences honor pause/stop; on sentence end, advance the cursor (and stream the next element/resource when the current one is exhausted). Persist progress via the existing `ProgressEntity.ttsLocator` (the field Plan 03/06 reserved) so resume and the library progress reflect listening position.

**Pause/resume:** `TextToSpeech.stop()` flushes (no native mid-utterance pause), so `pause()` stops the current utterance and remembers the cursor; `play()` resumes by re-speaking from the current sentence. `next/previousSentence` stop + move the cursor + speak.

---

## 5. Highlight + auto-scroll + the `ReaderDecorationController` refactor

When the reader is open it observes `controller.currentLocator` and `controller.state`; on each new sentence it applies a rose `Decoration.Style.Highlight` on group **`"tts"`** and, when `KanomPreferences.autoScroll` is on, calls `navigator.go(locator)`. Group `"tts"` is independent of `"bookmarks"` / `"probe"` (Plan 06 group semantics). When the reader is closed, playback continues audio-only (no highlight) — correct.

`ReaderViewModel` already owns a lot (the roadmap's first refactor candidate). To avoid bloating it, **extract `ReaderDecorationController`** — a small unit that holds the bound `EpubNavigatorFragment` and owns the three decoration groups (bookmarks, tts, probe) + auto-scroll. `ReaderViewModel` delegates to it. This folds in the roadmap's suggested refactor exactly where it pays off.

---

## 6. Background playback (media3)

- **New dependency:** AndroidX **media3** (`media3-session`, `media3-common`).
- **`KanomPlaybackService : MediaSessionService`** (foreground) holds a `MediaSession` backed by **`KanomTtsPlayer : SimpleBasePlayer`** (media3's helper for custom, non-ExoPlayer playback). The player maps `COMMAND_PLAY_PAUSE` → play/pause, `COMMAND_SEEK_TO_NEXT/PREVIOUS_MEDIA_ITEM` (or seek-to-next/prev) → next/previousSentence, `COMMAND_STOP` → stop; publishes `State` (playing/paused) + `MediaMetadata` (title, author, cover art) from `controller.state` so the lock-screen/notification render. media3 produces the `MediaStyle` notification automatically from the session.
- **Manifest:** declare the service (`<service android:foregroundServiceType="mediaPlayback">`) + `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions; `MEDIA_CONTENT_CONTROL` not needed.
- **Audio focus:** request on play, pause on transient loss / duck on transient-can-duck / resume on regain; pause on headset unplug (`ACTION_AUDIO_BECOMING_NOISY`). media3 handles much of this through the session.

---

## 7. Mini-player

A docked Compose `MiniPlayer` shown app-wide **whenever playback is active**: above the bottom nav on Library/Settings, and over the reader chrome. Shows cover + title, the **active voice chip** (e.g. ไทย), play/pause + prev/next-sentence, and a **sleep-timer chip**. Observes `controller.state`. A tap target (opens the Now-Playing screen) is wired but inert until Plan 08. Styling per the Anchan design system; rose stays reserved for the karaoke highlight (the mini-player uses primary/surface tokens).

---

## 8. Sleep timer

`SleepTimer` in the controller: fixed durations (5/10/15/30/45/60 min), **end-of-chapter** (stop at the next reading-order resource boundary), and a **fade-out** ramp (reduce utterance volume over the last seconds before stop). Default duration = `KanomPreferences.sleepTimerDefaultMin`. Set/cancel via the mini-player chip. The countdown logic is pure (virtual-time testable).

---

## 9. Voice routing & start trigger

`VoiceRouter`: for each `Lang` segment, pick the `Voice` + speed + pitch from `VoiceConfigRepository` (keyed by `Lang.name`); when unset, the **system default voice** for that locale + default speed/pitch. (Changing voices is Plan 08's settings UI.)

**Start triggers (two):**
1. **Play control** (mini-player / reader chrome) → start from the reader's current top-of-page `Locator`.
2. **Long-press a paragraph** → start from that paragraph. The reader's gesture overlay gains an `onLongPress` (alongside the existing 3-zone `onTap` + swipe), resolves the pressed point to a Readium `Locator`, and calls `controller.start(bookId, fromLocator)`. **Single tap is unchanged** (page-turn/chrome) — long-press is a distinct gesture, no regression. *(The point→`Locator` resolution mechanism — Readium `VisualNavigator` tap/selection→Locator, a JS hit-test in the WebView, or a "อ่านจากตรงนี้ / Read from here" action on Readium's long-press selection `ActionMode` — is an API-recon item for writing-plans; default to the selection-`ActionMode` action if a direct point→Locator isn't exposed.)*

---

## 10. Data, preferences & dependencies

- **`ProgressEntity.ttsLocator`** (reserved in Plan 03/06) now written by the controller — listening position, distinct from the reading position (`locator`). No Room migration (field exists).
- **`KanomPreferences`** consumed: `skipUrls` (Normalizer URL handling), `highlightOn` (toggle the tts Decoration), `autoScroll`, `sleepTimerDefaultMin`. `premiumUnlocked` gates nothing in Plan 07 (neural is Plan 08).
- **`VoiceConfig`** consumed (per-Lang voice/speed/pitch).
- **`ReplacementRuleRepository.observeEffective(bookId)`** → `ReplacementEngine` → injected into `TextPipeline` as its `TextTransform` (pre-synthesis pronunciation rules + the shipped default Thai pack).
- **New deps:** media3 (`media3-session` + `media3-common`).

---

## 11. Testing strategy

- **JVM / Robolectric (TDD):**
  - `TtsPlaybackController` **speak-loop sequencing** with a **fake `TtsProvider`** + **fake content source**: advances sentence→segment in order, routes each segment's language to the expected voice, honors pause/resume/next/previous, emits `currentLocator` per sentence, ends cleanly.
  - `VoiceRouter` (Lang → Voice/speed/pitch from VoiceConfig + defaults) — pure.
  - `SentenceLocators` (SpokenSentence + element locator → Readium `Locator`) — Robolectric (uses `Locator`).
  - `SleepTimer` countdown + end-of-chapter + fade trigger — virtual time.
  - `TextPipeline` + `ReplacementEngine` already covered (Plans 01–04).
- **Device / instrumented (the spike's deferred checklist lands here):** real speech; TH→th-TH / EN→en-US per-script routing with acceptable gaps; sentence highlight lands on correct boundaries + auto-scroll-follow; background continues with screen off; lock-screen/notification/headset controls; sleep timer + fade; Samsung `LANG_MISSING_DATA` fallback; `onRangeStart` usability (informs Phase-2 word highlight).

---

## 12. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Per-segment voice switching gaps/latency between languages (spec §8) | Pre-warm the engine; accept small gaps for v1; **sentence-level single-voice fallback** when a language voice is missing (`VoiceRouter` falls back; ladder word→sentence→paragraph guarantees > Shiori). |
| media3 `SimpleBasePlayer` modeling utterance-TTS (no seekable timeline) | Model a minimal `State` (playing/paused + current item, indeterminate position); **API-recon + a thin spike early in writing-plans** before building the mini-player on top. |
| Two open `Publication`s (reader display + TTS) memory | Controller opens its TTS publication only while playing and closes on stop/book-change; reader's is independent. |
| Large-book content streaming | Stream per reading-order resource/element with a cursor; never materialize the whole book. |
| `TextToSpeech` has no native pause | `pause` = stop + remember cursor; `resume` re-speaks the current sentence (sentence-granular is acceptable per spec). |
| Foreground-service / notification policy (Android 13/14) | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + media3's compliant notification; `POST_NOTIFICATIONS` runtime permission on 13+. |

---

## 13. Open items (resolve during planning — API recon)

- Exact **readium-media-tts** vs **our-own-engine** boundary we keep: we are NOT using Readium's `TtsNavigator`; confirm we only need `publication.content()` + the Decoration API (both verified in Plan 06's recon) — the `readium-navigator-media-tts` artifact may even become removable (verify).
- The **`Content` streaming primitive** (`elements()` vs `Content` iterator vs `content(startLocator)`) for per-resource cursoring.
- **media3 `SimpleBasePlayer`** required `getState`/`State` surface for a non-seekable custom player + the `MediaSessionService` notification wiring + the exact foreground-service-type/permission set for the target SDK.
- `android.speech.tts.TextToSpeech` voice enumeration per `Lang`/locale + the `synthesize`/`UtteranceProgressListener` contract (incl. `onRangeStart` availability per engine).
- Mini-player docking: app-wide host (the `KanomApp` Scaffold) vs per-screen — confirm the single insertion point above the bottom nav + over the reader.
- **Long-press → `Locator`:** how to resolve a long-press screen point in `EpubNavigatorFragment` to a Readium `Locator` (direct `VisualNavigator` tap/point API vs a WebView JS hit-test vs a "Read from here" action on the long-press selection `ActionMode`/`selectionActionModeCallback`). Pick the cleanest during recon; selection-`ActionMode` is the fallback.
