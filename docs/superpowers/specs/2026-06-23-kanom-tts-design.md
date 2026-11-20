# Kanom TTS — Design Spec

**Date:** 2026-06-23
**Status:** Approved (brainstorm complete) — pending spec review
**Package:** `com.nextz.kanomtts` · minSdk 26 · targetSdk/compileSdk 36 · AGP 9.2.1

---

## 1. Vision

**Kanom TTS** (ขนม = Thai sweets) is an Android EPUB reader built **TTS-first**, specialized for **Thai and mixed-language** books (Thai + English + Japanese + Chinese). It reads books aloud with a **karaoke-style highlight** that tracks the spoken sentence, switches voices automatically across scripts within a single paragraph, and wraps it all in a deliberately **"sweet"** brand — as delightful as its name.

The bar to beat is *Shiori EPUB Reader* (Thai-focused, closed-source). We match its strengths (per-language voice control, in-paragraph script switching, the text-replacement pronunciation engine, background playback) and beat it where it's weak: **synced highlighting** (Shiori is paragraph-only), a **sleep timer** (it has none), **real Thai word segmentation**, **no ads**, and a distinctive sweet design.

### Design pillars
1. **Listen, don't just read** — every interaction assumes the user is listening, possibly eyes-off.
2. **Sweet but calm** — playful brand, but reading/listening surfaces stay restful for hour-long sessions.
3. **Thai-first, multilingual-fluent** — Thai is the priority language; English/Japanese/Chinese are first-class within mixed text.
4. **Offline by default** — core experience never requires a network; premium neural voices run on-device.

---

## 2. Decisions Locked in Brainstorming

| Topic | Decision |
|---|---|
| **Monetization** | Free core, **no ads**. One-time **premium unlock** (IAP) for on-device neural voices (and later, car mode). |
| **Voice quality (v1)** | Ship **both**: (a) Android **system TTS** as the free default for all 4 languages; (b) on-device **neural** voice as the premium unlock, **Thai first**, other languages as downloadable packs later. |
| **Highlight granularity (v1)** | **Sentence-level** karaoke highlight (word-level is Phase 2). Fallback ladder: word → sentence → paragraph. |
| **Languages (v1)** | **Thai + English + Japanese + Chinese**, with in-paragraph script routing. |
| **Reader/EPUB foundation** | **Readium Kotlin Toolkit** (BSD-3), pending a build-compat spike against AGP 9.2.1 / compileSdk 36. Fallback: hand-rolled WebView + JS. |
| **UI stack** | Greenfield **Jetpack Compose + Material 3 Expressive** (replace the empty AppCompat/Views scaffold). |
| **Background overlay** | **Defer** Shiori's floating draw-over-apps overlay to Phase 3 (Play-Store review risk). v1 = MediaSession + foreground service + notification/lock-screen. |
| **Palette** | **"Anchan & Coconut"** — butterfly-pea blue, coconut cream, rose-magenta highlight. |
| **Typography** | **Itim** (brand/display/headings) + **Sarabun** (reading body). |

### Non-goals (v1)
Ads · accounts/cloud sync · cross-device progress · PDF/MOBI/other formats · floating overlay · word-level highlight · in-app web translate (use on-device ML Kit in Phase 2) · Android Auto/Wear.

---

## 3. Architecture

### 3.1 Tech stack
- **UI:** Jetpack Compose, **Material 3 Expressive** (dynamic color seeded from the Anchan palette; expressive motion/shape-morph).
- **Reader:** Readium Kotlin Toolkit (EPUB navigator, `ContentService`, `Locator`, `TtsNavigator` + `Decoration` for highlight). Reader screen uses **Compose ↔ Fragment interop** (Readium's renderer is a `Fragment`).
- **Storage:** Room (books, bookmarks, replacement rules, reading progress) + DataStore (preferences).
- **DI:** Hilt. **Async:** Coroutines + Flow. **Images:** Coil.
- **Billing:** Google Play Billing (one-time premium entitlement).

### 3.2 Module structure (pragmatic multi-module — adjustable)
```
:app                      Application, Hilt graph, NavHost, Kanom theme wiring
:core:designsystem        M3 Expressive theme, Anchan palette, Itim/Sarabun type, components, motion
:core:model               Domain models (Book, Chapter, Bookmark, ReplacementRule, VoiceConfig…)
:core:data               Room DB + DataStore + repositories
:feature:library          Import, library grid/list/detail, continue-listening hero
:feature:reader           Readium reader (Compose+Fragment), display settings, bookmarks UI
:feature:player           Mini-player, Now-Playing screen, sleep timer
:feature:settings         Voices, replacement-rule editor, themes, premium unlock
:tts:core                 TtsProvider interface, PlaybackSession, MediaSession, foreground service
:tts:system               Android system TTS provider
:tts:neural               On-device neural provider (sherpa-onnx)  [premium]
:text:pipeline            Normalizer + script-run splitter + sentence tokenizer + Han disambiguation
:text:replacement         Replacement-rule engine (Aho-Corasick literals + batched regex)
```
Boundary rule: each module exposes a narrow interface; the TTS engine is the only swappable backend behind `TtsProvider`. Modules can be collapsed if the overhead isn't worth it for a solo build.

### 3.3 The TTS pipeline (shared across all engines & languages)
```
EPUB text (Readium ContentService, reading order)
  → Normalizer        (Thai digits → words, abbreviations, optional "Skip URLs")
  → Replacement       (user + default Thai pronunciation rules, pre-synthesis)
  → Script-run split  ([ไทย][English][日本語][中文] spans via Character.UnicodeScript)
  → Han disambiguation (book dc:language + Hiragana/Katakana proximity → JP vs ZH)
  → Sentence tokenize (ICU BreakIterator, Thai-aware + heuristic splitter)
  → Voice routing     (per-span language → configured Voice/Speed/Pitch)
  → TtsProvider.speak (System TTS  OR  Neural)
  → boundary events   (onRangeStart / utterance callbacks)
  → Highlight         (Readium Decoration on the sentence Locator) + auto-scroll-follow
```

---

## 4. TTS Engine Strategy & Language Routing

### 4.1 Two providers behind one interface
```
interface TtsProvider {
  suspend fun availableVoices(lang): List<Voice>
  fun speak(utterance: Utterance, voice, speed, pitch)
  val events: Flow<TtsEvent>   // Started, RangeStart(start,end), Done, Error
  fun stop(); fun pause(); fun resume()
}
```
- **`SystemTtsProvider`** (free) — wraps `android.speech.tts.TextToSpeech`; user can choose installed engine (Google/Samsung/vendor); per-language `Voice` + `setSpeechRate`/`setPitch`. Handles `LANG_MISSING_DATA` with an install-voice prompt.
- **`NeuralTtsProvider`** (premium) — sherpa-onnx ONNX runtime, **Thai neural voice in v1**. **Only commercially-licensed models** (e.g. no CC-BY-NC `mms-tts-tha`). Other languages downloadable later.

### 4.2 Mixed-language / code-switching
- Split each paragraph into **script runs**; synthesize each run as its own utterance with the right voice/locale, concatenated seamlessly (Shiori's headline trick, applied to 4 languages).
- **Han (漢字/汉字) disambiguation** between Japanese and Chinese:
  1. Per-book **primary language** from EPUB `dc:language` metadata.
  2. Statistical sniff: presence of Hiragana/Katakana ⇒ Japanese.
  3. Local context: Han run adjacent to kana ⇒ Japanese; else fall to book primary.
  4. **User override** per book.

### 4.3 Per-language controls
Independent **Voice / Speed (×0.5–×3.0) / Pitch (×0.5–×2.0)** per language, each with **preview playback** before reading.

---

## 5. Feature Set

### 5.1 MVP (v1)
**Library & import**
- Import via file picker **and** OS "open with"; library **grid / list / detail** views; cover thumbnails; reading-progress %; **duplicate detection** (filename + size); search/filter; **Continue-listening hero** card.

**Reader**
- Readium rendering; **exact resume** (incl. where TTS stopped); chapter list; infinite scroll.
- Display: **Light / Dark / Sepia / true-black**; font family/size/line-height/margins; CSS-override escape hatches (disable EPUB styles / force fonts / strip colors). Bundled **Sarabun**; **Thai line-break injection** (U+200B/`<wbr>`).
- **Bookmarks** (any paragraph, searchable, visually outlined).

**TTS (the heart)**
- Free system TTS (TH/EN/JP/ZH); premium **neural Thai**.
- Per-language Voice/Speed/Pitch + preview; **tap-any-paragraph to start**; transport (prev/play-pause/next/stop); **auto-scroll-follow**; **"Skip URLs"**.
- **In-paragraph script routing** across 4 languages + Han disambiguation.
- **Sentence-level karaoke highlight** (toggleable; word→sentence→paragraph fallback).
- **Sleep timer** (durations + end-of-chapter + fade-out).
- **Text Replacement engine** (literal + regex, **TSV import/export**) **+ shipped default Thai pronunciation pack**; rules apply pre-synthesis and optionally in-reader.
- Thai **number/abbreviation normalization**.
- **Background playback:** MediaSession + foreground media service + lock-screen/notification controls; persistent **mini-player**.

**Brand/UX**
- Compose + M3 Expressive; **Anchan & Coconut** palette; **Itim + Sarabun**; mini-player; sweet microinteractions.

### 5.2 Phase 2
Word-level highlight · on-device **ML Kit translate-on-tap** · neural voice packs (EN/JP/ZH) · **Big Player / Car Mode** (eyes-free, doubles as low-vision) · OpenDyslexic option · shareable replacement-rule packs.

### 5.3 Phase 3 / maybe-never
Floating draw-over-apps overlay · cloud cross-device sync · PDF/MOBI · Android Auto/Wear · RSVP speed-reading.

---

## 6. Visual Design — "Sweet but calm"

### 6.1 Palette: Anchan & Coconut
| Role | Hex |
|---|---|
| Primary (butterfly-pea blue) | `#5D63D6` |
| Primary light / container | `#EBECFB` |
| Primary gradient end | `#7B82F0` |
| Highlight — karaoke (rose) | `#FF5E9C` |
| Highlight background | `#FFE0EC` |
| Highlight text | `#9E2B5E` |
| Background (coconut cream) | `#FBF7EF` |
| Surface | `#FFFFFF` |
| Ink / on-surface | `#2B2A33` |
| Muted | `#A79E8E` |

- Seed M3 Expressive dynamic color from `#5D63D6`; reserve **rose `#FF5E9C`** exclusively for the karaoke highlight so it always reads as "this is being spoken."
- Reader themes derive cream/sepia/dark/true-black variants; all pairs meet **WCAG AA** for body text.

### 6.2 Typography
- **Brand/display/headings:** **Itim** (soft handwritten rounding = the sweetness).
- **Reading body (default):** **Sarabun** (clearest Thai reading sans), user-changeable (Sarabun / Noto Serif Thai / system).
- **Fallbacks:** Noto Sans JP (Japanese), Noto Sans SC (Chinese).

### 6.3 Signature screens (mocked & approved)
- **Library:** Continue-listening hero (blue gradient + play), cover grid with **progress rings**, bottom nav (คลัง / กำลังฟัง / ตั้งค่า).
- **Reader + karaoke:** rose **highlight pill** tracking the spoken sentence; inline mixed Thai+English; **persistent docked mini-player** with live waveform + active voice chip (TH).
- **Now Playing:** large cover, "currently speaking" sentence card, transport, **speed/pitch/sleep-timer** chips, **per-language voice switcher** (ไทย/EN/日本/中文).

### 6.4 Motion & delight
M3 Expressive spring physics; shape-morph on play/pause; gentle highlight-pill slide between sentences; tasteful "kanom" microinteractions (e.g. subtle bounce on bookmark). Keep motion **reducible** (respect system reduce-motion) so it never tires long-session listeners.

### 6.5 Accessibility
Large-text support, high-contrast/true-black, TalkBack labels on all controls, eyes-off operation via lock-screen/notification, generous touch targets. (OpenDyslexic + low-vision Big Player in Phase 2.)

---

## 7. Data Model (Room, sketch)
- **Book**(id, uri, title, author, lang, coverPath, fileSize, addedAt, lastOpenedAt)
- **Progress**(bookId, locator, percent, ttsLocator, updatedAt)
- **Bookmark**(id, bookId, locator, snippet, createdAt)
- **ReplacementRule**(id, scope[global|book], pattern, isRegex, replacement, order, enabled)
- **VoiceConfig**(lang, providerId, voiceId, speed, pitch)
- Preferences (DataStore): theme, font, size, line-height, margins, skipUrls, highlightOn, autoScroll, sleepTimerDefault, premiumUnlocked.

---

## 8. Biggest Risk & De-risking Spike

**Risk:** the signature chain — mixed-Thai playback + synced highlight — depends on Thai segmentation and engine timing that are notoriously unreliable: Thai has no inter-word spaces (coarse `onRangeStart`), single th-TH locale mangles embedded English, ICU sentence-splitting is weak for Thai, and OEM devices may lack a good Thai voice (poor OS voice gets blamed on Kanom).

**Spike (do first, before committing UX), validate in order:**
1. Readium builds against **AGP 9.2.1 / compileSdk 36**; if not, fall back to hand-rolled WebView+JS.
2. Readium `TtsNavigator` + a **custom ICU Thai tokenizer** yields sensible utterances and Decoration highlights land on correct Thai sentence boundaries on 2–3 real Thai EPUBs.
3. Per-script-run splitting routes TH→th-TH / EN→en-US (and JP/ZH) as separate utterances with acceptable gaps.
4. `onRangeStart` is usable on the Google engine for Thai; if not, **ship sentence-granular** (still beats Shiori) and treat word-level as Phase 2.
5. `isLanguageAvailable` / `LANG_MISSING_DATA` + install-voice prompt verified on a Samsung device.

The **word → sentence → paragraph fallback ladder** guarantees even a worst-case spike still ships better than Shiori.

---

## 9. Testing Strategy
- **Unit:** normalizer, replacement engine (literal + regex + ordering), script-run splitter, Han disambiguation, sentence tokenizer (Thai/English/JP/ZH fixtures).
- **Instrumented:** Room migrations, EPUB import, resume-exact-position, MediaSession/foreground-service lifecycle, sleep-timer behavior.
- **Manual matrix:** 2–3 real Thai + mixed-language EPUBs across a Pixel (Google TTS) and a Samsung device; verify highlight sync, voice switching, background playback, lock-screen controls.
- TDD for the `:text:*` modules (pure logic, high-value, deterministic).

---

## 10. Open Items (resolve during planning)
- Confirm Readium artifact compatibility (spike #1).
- Pick the specific **commercially-licensed Thai neural model** for sherpa-onnx (license + size + quality).
- Decide module granularity (full split vs collapsed) for the solo build.
- Define exact M3 Expressive dark / sepia / true-black token values from the Anchan seed.
