# Integration Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. NOTE: This is a *spike* (de-risking investigation), not a production feature. Task 1 is subagent-automatable; Task 2 is a MANUAL device checklist for the human — do not attempt to execute it with a subagent.

**Goal:** Answer the design spec's open risks (§8) with evidence before committing to the reader UX: (1) does the **Readium Kotlin Toolkit resolve and compile** against this project's AGP 9.2.1 / compileSdk 36 toolchain, and what is the exact API surface for opening an EPUB + driving TTS + plugging a custom Thai tokenizer; (2) on a real device, does `onRangeStart` fire usefully for Thai, does the highlight land on the right sentence, does per-script voice switching work, and does the voice-install flow behave on a Samsung device.

**Architecture:** Add Readium to the existing `:app` module as a throwaway probe (removed/relocated to a real `:reader` module in Plan 06 if GO). The probe is minimal code that references the open-publication, TTS-navigator, and text-tokenizer APIs so the compiler must resolve them; `assembleDebug` then proves resolution + compilation + manifest merge + dexing against the real toolchain. Device behavior is validated manually with a fixed checklist and a go/no-go decision feeding the **word → sentence → paragraph** highlight fallback ladder.

**Tech Stack:** Readium Kotlin Toolkit v3.x (`org.readium.kotlin-toolkit:*`, Maven Central), Android (`:app`), Kotlin, Gradle.

## Global Constraints

- Project toolchain: minSdk 26, compileSdk 36, AGP 9.2.1, Kotlin 2.1.0 — copied from spec §0 and Plan 01.
- Reader foundation decision is **Readium pending this spike**; fallback is hand-rolled WebView+JS — copied from spec §3.1 / Decision A.
- v1 highlight is **sentence-level**; the fallback ladder is **word → sentence → paragraph** (worst-case still beats Shiori's paragraph-only) — copied from spec §2, §8.
- Languages: Thai + English + Japanese + Chinese; per-script voice routing — copied from spec §4.
- This spike must reuse the Thai tokenizer already built in Plan 01 (`com.nextz.kanomtts.text.pipeline.SentenceTokenizer` / `TextPipeline`) — do not write a second tokenizer.
- A spike's deliverable is **evidence + a decision**, not shipped features. "I researched the API and it does not compile, here is the error" is a successful Task 1 outcome (it answers the go/no-go).

---

## File Structure

```
gradle/libs.versions.toml          (modify: add Readium version + library aliases)
settings.gradle.kts                (modify ONLY if Readium needs an extra repo)
app/build.gradle.kts               (modify: add Readium deps; enable Compose ONLY if needed by probe — prefer not)
app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt   (create: minimal API-surface probe)
docs/superpowers/spikes/2026-06-23-readium-findings.md         (create: the findings + go/no-go report)
```

---

### Task 1: Readium build-compatibility probe + API-surface reconnaissance  (SUBAGENT)

Prove (or disprove) that Readium resolves and compiles against the project toolchain, and document the concrete APIs Plan 06/07 will use — especially how a **custom Thai sentence tokenizer** is plugged into Readium's TTS/content tokenization.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify (only if required): `settings.gradle.kts`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt`
- Create: `docs/superpowers/spikes/2026-06-23-readium-findings.md`

**Interfaces:**
- Consumes: nothing at runtime (probe is compile-only dead code).
- Produces: a findings doc with a clear **GO / NO-GO** verdict and, if GO, the exact artifact coordinates + the API calls for: opening an EPUB (`AssetRetriever` / `PublicationOpener` or `Streamer`), reading content in reading order (`publication.content()` / `ContentService`), the TTS navigator (`org.readium.navigator.media.tts` — `TtsNavigatorFactory`, the Android TTS engine), and the text tokenizer SPI used to split content into utterances (the interface a custom Thai tokenizer would implement, e.g. a `TextTokenizer`/`ContentTokenizer` with `TextUnit.SENTENCE`).

- [ ] **Step 1: Research current Readium coordinates + API (do this first, cite sources in the findings doc)**

Use the context7 MCP (`resolve-library-id` → `query-docs` for "readium kotlin toolkit") and/or web/Maven Central to confirm, as of now:
- The latest stable `org.readium.kotlin-toolkit` version and the exact module artifacts needed (at minimum `readium-shared`, `readium-streamer`; for TTS the navigator media-tts artifact).
- Whether any extra Maven repo is required (v3.x is on Maven Central; older needed JitPack). `settings.gradle.kts` already has `mavenCentral()` under `dependencyResolutionManagement` with `FAIL_ON_PROJECT_REPOS` — if an extra repo is needed, add it THERE, not in a module.
- The minSdk/compileSdk Readium declares (must be compatible with our minSdk 26 / compileSdk 36).
- The API for: open EPUB → get reading-order content → TTS navigator → the tokenizer SPI for custom sentence splitting.
Record findings (versions, coordinates, API snippets, source URLs) in `docs/superpowers/spikes/2026-06-23-readium-findings.md` as you go.

- [ ] **Step 2: Add the catalog entries**

In `gradle/libs.versions.toml` add a `readium` version under `[versions]` and the needed library aliases under `[libraries]` (use the coordinates confirmed in Step 1). Example shape (replace version/artifacts with the verified ones):
```toml
# [versions]
readium = "3.0.3"
# [libraries]
readium-shared = { group = "org.readium.kotlin-toolkit", name = "readium-shared", version.ref = "readium" }
readium-streamer = { group = "org.readium.kotlin-toolkit", name = "readium-streamer", version.ref = "readium" }
readium-navigator = { group = "org.readium.kotlin-toolkit", name = "readium-navigator", version.ref = "readium" }
# add the media-tts navigator artifact as confirmed in Step 1
```

- [ ] **Step 3: Add the dependencies to `:app`**

In `app/build.gradle.kts` add the Readium `implementation(...)` deps. Do NOT enable Jetpack Compose for this probe unless a referenced Readium API forces it — keep the probe minimal. If Readium requires Java 17 / a higher desugaring level than the project's Java 11, note it in findings and set what's needed minimally to compile.

- [ ] **Step 4: Write the compile-only probe**

Create `app/src/main/kotlin/com/nextz/kanomtts/spike/ReadiumProbe.kt` — an `object` (or function) that imports and *references* (constructs / calls, in unreachable or `@Suppress("unused")` code) the key Readium types from Step 1: opening a publication, accessing reading-order content, constructing the TTS navigator factory, and the tokenizer SPI. The point is to force the compiler to resolve the real API surface. Reuse our tokenizer: include a sketch (compiling) of how `com.nextz.kanomtts.text.pipeline.SentenceTokenizer` would adapt to Readium's tokenizer interface — if the interface can be implemented, write the adapter shell; if not, write a comment documenting why and what shape is needed. Add `implementation(project(":text:pipeline"))` to `:app` if you reference it.

- [ ] **Step 5: Compile against the real toolchain**

Run: `./gradlew :app:assembleDebug`
Expected (GO): `BUILD SUCCESSFUL` — Readium resolved, compiled, merged, and dexed against AGP 9.2.1 / compileSdk 36.
If it FAILS: capture the exact error (dependency resolution? manifest merge? minSdk conflict? Java version? duplicate classes?). Try the minimal, documented adjustment (e.g. bump Java target, add a required repo). If it cannot be made to build after reasonable effort, that is a valid **NO-GO** result — record the blocker precisely.

- [ ] **Step 6: Write the findings + verdict**

Complete `docs/superpowers/spikes/2026-06-23-readium-findings.md` with:
- **Verdict: GO / NO-GO** for Readium as the reader foundation (compile/resolve dimension).
- Exact versions, artifact coordinates, any extra repo, any toolchain bump required.
- The concrete API for open-EPUB → reading-order content → TTS navigator → custom tokenizer SPI (with the snippet from the probe).
- How the Plan 01 Thai tokenizer plugs in (adapter shape), or why it can't and what the alternative is.
- If NO-GO: the blocker + the recommendation to fall back to WebView+JS (spec Decision A fallback), with what that changes for Plans 06/07.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/ settings.gradle.kts docs/superpowers/spikes/
git commit -m "spike: probe Readium build-compat against AGP 9.2.1 + document TTS/tokenizer API"
```

---

### Task 2: Device validation checklist  (MANUAL — the human runs this; NOT a subagent task)

The behaviors below cannot be observed from a compile. They require the app running on a real device/emulator (including, ideally, one Pixel/Google-TTS device and one Samsung device). Run after Task 1 returns GO. This task is executed by the human; a subagent must NOT mark it complete.

**Prereq:** a minimal runnable reader is needed to observe these. If Task 1's probe is compile-only, the smallest runnable harness (a single Activity that opens a bundled Thai EPUB with Readium's TTS navigator using our tokenizer) is built as the FIRST task of Plan 06, and this checklist is then run against it. Do not block the spike's compile verdict on having the runnable harness.

**Checklist (record pass/fail + notes for each):**
- [ ] **V1 — `onRangeStart` fires for Thai (Google engine):** with a Thai-only paragraph, does the engine emit range callbacks mid-utterance, or only at utterance start/end? → if useful sub-utterance ranges: word-level highlight is feasible (Phase 2); if only whole-utterance: confirm **sentence-level** is the v1 floor.
- [ ] **V2 — Highlight lands on the correct sentence:** does the Readium Decoration (driven by our `SentenceTokenizer` boundaries) visually highlight the exact sentence being spoken on 2–3 real Thai EPUBs? Note any drift/off-by-one.
- [ ] **V3 — Per-script voice routing:** in a paragraph mixing Thai + English (+ JP/ZH if available), does each run speak in the right voice with acceptable inter-run gaps? (Uses Plan 01's script-run split + Han disambiguation.)
- [ ] **V4 — Voice availability / install flow:** on a Samsung device, does `isLanguageAvailable` / `LANG_MISSING_DATA` correctly detect a missing Thai voice and does the install-voice prompt work? Does OEM Thai-voice quality differ enough to matter?
- [ ] **V5 — Background playback sanity:** does TTS continue with screen off via the foreground service + notification (smoke test only; full impl is Plan 07)?

**Decision gate (record in the findings doc):** set the v1 highlight granularity on the **word → sentence → paragraph** ladder based on V1/V2, and confirm/deny per-script routing (V3) and the voice-install UX (V4). This feeds Plans 06–07.

---

## Self-Review

**1. Spec coverage** (against spec §8 spike items):
- Readium builds vs AGP 9.2.1 / compileSdk 36 → Task 1 Step 5. ✓
- Readium TtsNavigator + custom ICU Thai tokenizer produces sensible utterances/highlight → Task 1 documents the SPI (compile); Task 2 V1/V2 validates behavior on device. ✓
- Per-script-run routing TH/EN/JP/ZH → Task 2 V3. ✓
- `onRangeStart` usable for Thai; else sentence-granular floor → Task 2 V1 + decision gate. ✓
- `isLanguageAvailable` / `LANG_MISSING_DATA` + install prompt on Samsung → Task 2 V4. ✓
- Fallback ladder word→sentence→paragraph → decision gate. ✓

**2. Placeholder scan:** The version/coordinate examples in Task 1 are explicitly "verify in Step 1 and replace," not placeholders to ship — Step 1 mandates confirming real values before use. Task 2 is intentionally a manual checklist (not code). No "TODO/TBD/implement later." ✓

**3. Type consistency:** Reuses `com.nextz.kanomtts.text.pipeline.SentenceTokenizer` / `TextPipeline` from Plan 01 (no redefinition). Readium types are named as candidates to be confirmed in Step 1, since the exact SPI is what the spike exists to discover. ✓

**Note for executor:** Task 1 is "research + probe + report." If `assembleDebug` fails, that is a legitimate finding, not a task failure — report NO-GO with the precise blocker rather than forcing a build. Do NOT spend effort building a full reader UI in this task; the runnable harness belongs to Plan 06.
