# Kanom TTS — TODO

_Handoff as of 2026-06-25. Branch `master` @ `b5af4ec`, everything below the line is merged + pushed to origin (origin caught up)._

Full project context lives in auto-memory (`MEMORY.md` → `kanom-tts-roadmap.md`, `kanom-tts-build-workflow.md`) and loads automatically each session.

---

## ▶ Next up

### Select & Translate (แปล menu) — the only scoped-but-unbuilt feature
The reader's text-selection action bar already has a **"แปล" (Translate)** entry wired as a **"coming soon" stub** (added in the Plan 07 selection-menu work, alongside Start-TTS / Copy / Add-Replacement). Build it out:
- Pick a translation path (on-device ML Kit translate vs. a cloud API — decide in brainstorming; offline-first fits the app).
- Show the translation for the selected text (bottom sheet or popup).
- TH⇄EN at minimum; the app is mixed TH/EN/JA/ZH so consider source-language detection.
- **Run it as its own cycle:** brainstorming → spec → writing-plans → subagent-driven-development → device sign-off → merge (the established cadence; ultracode on).

---

## ◻ Small backlog (benign, fold in opportunistically)

- **08a — sleep chips hardcode 15 min.** `NowPlaying`/`MiniPlayer` sleep-timer chips should read the already-exposed `sleepTimerDefaultMin` pref instead of a literal 15.
- **08a — `preview` off-scope hardening.** `TtsPlaybackController.preview` reads `playJob`/`_state` off `@PlaybackScope`; safe only via the both-scopes-are-Main coupling. Wrap the body in `withContext(scope.coroutineContext)` + widen the guard from `if Playing` to `!is Idle`.
- **09b — sentence-boundary highlight edge.** Visual Replacement re-derives the highlight quote per sentence-substring while the injector transforms whole text nodes; a pattern spanning a sentence boundary could fail to match → highlight silently drops for that sentence (no crash, best-effort by design). Only worth touching if it shows up on device.
- **Library delete — minor test polish.** Add a "delete a book with null coverPath" assertion to `DefaultBookDeleterTest`; the `Checkbox.onCheckedChange` double-hit-target with `combinedClickable` is functionally fine.

---

## ✅ Done & shipped (do not rebuild)

All built subagent-driven (TDD where pure) + two-stage per-task review + opus whole-branch review + your device sign-off, then merged `--no-ff` + pushed.

- **Plans 01–07** — core reader + library + TTS playback + Thai ZWSP word-break + sentence-level karaoke highlight + reader selection menu (@ `b9f16c9`).
- **08a** — Now-Playing screen + per-language voice/speed/pitch settings (@ `21b4213`).
- **09a** — Replacement **Editor** + reader Add-Replacement (global + per-book rules, CRUD, drag-reorder, TSV import/export, regex validated before save) (@ `8684e6f`).
- **09b** — **Visual Replacement** (render `showInReader`-flagged rules in the reader; highlight re-derived through the same engine so it stays aligned; spoken text untouched; Room v1→v2 migration) (@ `519c025`).
- **Library delete** — long-press multi-select bulk delete with full cleanup cascade (SAF permission, cover file, orphan Progress/Bookmark/book-scoped rules; global rules safe; EPUB content:// never deleted) + TTS-stop for a deleted playing book (@ `b5af4ec`).

### Dropped (user decision, 2026-06-25) — out of the roadmap
- **08b** — neural sherpa-onnx premium provider + Play Billing unlock.
- **08c** — word-level highlight via `onRangeStart`.

The app ships **system-TTS-only + sentence-level highlight** (already beats Shiori). The `TtsProvider`/`VoicePreviewer` seams + the sentence-highlight pipeline remain, so 08b/08c could be revived later if ever wanted.

---

## How to start the next piece

Say e.g. _"build Select & Translate"_ (or _"do the backlog items"_) and it runs the superpowers chain: **brainstorming → writing-plans → subagent-driven-development → finishing-a-development-branch**, with a recon Workflow up front and your device sign-off gating each merge. Specs land in `docs/superpowers/specs/`, plans in `docs/superpowers/plans/`, the SDD ledger in `.superpowers/sdd/progress.md`.
