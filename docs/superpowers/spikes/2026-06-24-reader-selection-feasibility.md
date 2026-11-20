# Reader Precise-Selection Feasibility Recon

- **Date:** 2026-06-24
- **Purpose:** Brainstorming-phase recon for the precise text-selection capability (touch → exact Locator + selected text + action menu) underpinning long-press-TTS-start, Select & Translate, Add Replacement, Visual Replacement. 3 parallel Readium-3.3.0 recon agents → synthesis.

# Readium 3.3.0 Selection-Feasibility Brief (Kanom TTS Reader)

Consolidated recon to seed a brainstorming/design session for precise text-selection + a customizable selection menu + gesture reconciliation with the device-tuned page-nav.

Sources verified in-repo:
- `/Users/altered/Desktop/Works/Repo3/GitHub/KanomTTS/app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderChrome.kt`
- `/Users/altered/Desktop/Works/Repo3/GitHub/KanomTTS/app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt`
- `/Users/altered/Desktop/Works/Repo3/GitHub/KanomTTS/app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderScreen.kt`
- `/Users/altered/Desktop/Works/Repo3/GitHub/KanomTTS/app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderDecorationController.kt`
- `/Users/altered/Desktop/Works/Repo3/GitHub/KanomTTS/app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ThaiZwspInjector.kt`

---

## 1. Precise Locator + selected TEXT + customizable menu — and HOW

**Verdict: YES, via NATIVE WebView text selection (not a JS hit-test).** This is the only supported press→Locator route in 3.3.0; there is NO public point→Locator hit-test (`TapEvent` carries only `PointF`; `firstVisibleElementLocator()` is coarse top-of-screen).

**APIs:**
- `EpubNavigatorFragment` implements `SelectableNavigator` (also `DecorableNavigator`, `OverflowableNavigator`, `HyperlinkNavigator`).
- `interface SelectableNavigator { suspend fun currentSelection(): Selection?; fun clearSelection() }` — `currentSelection()` is **suspend**.
- `data class Selection(val locator: Locator, val rect: RectF?)` — **only two fields. There is NO `Selection.text`.** Selected text lives at `selection.locator.text.highlight` (`Locator.Text.highlight: String?`), with `before`/`after` context (~200 chars, word-trimmed).
- The repo already consumes this correctly at `ReaderViewModel.kt:210-211` (`currentSelection()?.locator`).

**Granularity (word vs sentence vs caret):** Readium adds NO expansion. `selection.js` just reads `window.getSelection()/getRangeAt(0)`. Granularity is whatever the Android System WebView produces: **long-press selects the WORD under the finger; drag the handles to extend.** Varies slightly by OEM/WebView version → verify on device matrix. No API to programmatically `selectWordAt(point)`.

**Highlighting the resolved range:** `DecorableNavigator.applyDecorations(decorations, group)` (suspend) with `Decoration(id, locator = selection.locator, style = Decoration.Style.Highlight(tint, isActive=true))`. Guard with `supportsDecorationStyle(Highlight::class)`; only renders after page load. Repo already does this in `ReaderDecorationController.kt`.

---

## 2. Gesture reconciliation — options + RECOMMENDATION + regression risk

**THE ROOT-CAUSE FINDING (and a live repo contradiction — see contradictions):** the current full-size Compose overlay `Box` in `ReaderChrome.kt:76-110` is a **sibling at a higher z-index than the AndroidView/WebView**. By Compose hit-test rules, the embedded WebView (not in the Compose pointer chain) NEVER receives the pointer stream. So the overlay's `detectTapGestures(onLongPress = { onLongPressRead() })` at `ReaderChrome.kt:80-83` **absorbs long-press** → native WebView selection never starts → the wired `selectionActionModeCallback` ("อ่านจากตรงนี้", `ReaderViewModel.kt:201-222`) effectively cannot fire from a real text selection.

Three reconciliation options evaluated:

- **(a) Drop the Compose overlay; drive nav via Readium `InputListener`.** `navigator.addInputListener(...)`; `InputListener.onTap(TapEvent): Boolean` / `onDrag(DragEvent): Boolean`. Critically, `onTap` fires **only after the WebView's internal handling** (links AND long-press selection) — so InputListener nav does NOT steal long-press. Native selection + ActionMode work unmodified.
- **(b) Compose long-press pass-through (keep overlay, forward long-press to WebView).** **Not viable cleanly.** No-consume CANNOT forward to a sibling AndroidView (it only forwards within the same Compose node chain). The only mechanism is `Modifier.pointerInteropFilter { false }` on raw `MotionEvent`s, which forces hand-rolled tap/long-press/drag discrimination and directly threatens the device-tuned nav. Highest regression risk, least determinism.
- **(c) Custom JS hit-test (keep overlay, on long-press call `evaluateJavascript(document.caretRangeFromPoint → word → Locator)`).** Feasible (~1-2 days, medium risk). `EpubNavigatorFragment.evaluateJavascript(script): String?` is stable public; round-trip pattern `runJavaScriptSuspend(...).let { JSONObject(it) }.let { Locator.fromJSON(it) }`. Risks: devicePixelRatio/scroll/column coordinate transform (gestures.js multiplies by `devicePixelRatio`); **Thai has no word boundaries** so JS word-expansion won't match Thai words (must snap to the app's own `ThaiZwspInjector`/tokenizer segmentation). Keep ONLY as a fallback if product needs a SILENT word-precise "start here" without showing native selection handles.

**RECOMMENDATION: (a).** Ranking across all three recon passes is unanimous: **(a) > (c) > (b)**.

**Regression risk to device-tuned tap/swipe nav: LOW and localized.** Two surfaces:
1. **Edge-tap zones are EDGE-based, not strict thirds.** `DirectionalNavigationAdapter` defaults: `horizontalEdgeThresholdPercent=0.3` → ~30% left / 40% dead-center / 30% right, vs the repo's current 33/33/33 (`ReaderChrome.kt:85-90`). Re-tune `horizontalEdgeThresholdPercent≈0.333` to match. It cannot make the CENTER toggle chrome — that needs a second custom listener.
2. **Swipe page-turn is NOT surfaced as a built-in InputListener-driven page turn.** `onDrag` is exposed but unwired by default; the paginated WebView's internal horizontal paging is LTR-correct but not tunable via the public API. Either accept native paging or reimplement `onDrag` with your own thresholds. Recommend leaning on edge-taps + keys; treat swipe as optional.

---

## 3. Menu customization extent

**Fully customizable, and you CAN suppress system items today — no special API needed.**

- `Configuration.selectionActionModeCallback: android.view.ActionMode.Callback?` (a plain `Callback`, NOT `Callback2`).
- `R2BasicWebView.startActionMode` passes YOUR callback as the entire menu builder via `startActionModeForChild(...)`. **Readium never auto-adds or auto-clears system Copy/Select-All** — the menu contains ONLY what your `onCreateActionMode` adds (return `true`). The system menu is used only when `selectionActionModeCallback == null`.
- **Multiple custom items:** yes — one `menu.add(...)` per item: Start-TTS / Copy / Translate / Add-Replacement.
- **Positioning:** on API 23+ Readium wraps your callback in an inner `Callback2Wrapper` (`ActionMode.Callback by callback, ActionMode.Callback2()`) forwarding `onGetContentRect`, so the floating menu positions correctly over the selection.
- Current repo usage adds a single item (`ReaderViewModel.kt:201-222`); extend the same `onCreateActionMode` with the additional items.
- `disableSelectionWhenProtected` (`@DelicateReadiumApi`) is DRM/LCP-only — irrelevant for sideloaded unprotected EPUBs; leave default.

---

## 4. ZWSP-in-selected-text caveat

`ThaiZwspInjector` injects **U+200B** at Thai word boundaries into the DISPLAY pub (`ThaiZwspInjector.kt:18`, used by `PublicationProvider`). `selection.js` normalizes `highlight` only with `.trim().replace(/\n/g,' ').replace(/\s\s+/g,' ')`, and **JS `\s` does NOT match U+200B** → the ZWSP **survives into `locator.text.highlight`** (and `before`/`after`).

**Consequence:** any TEXT-CONSUMING action (Copy / Translate / Add-Replacement) MUST strip U+200B from `highlight` before use (`.replace(/​/g, "")`), else clipboard/translator/replacement keys carry invisible zero-width spaces. The **TTS Start-from-here path is unaffected** — it uses `locator` positionally against the un-transformed content. (Marked medium confidence: JS regex behavior is certain; confirm end-to-end on a ZWSP-injected Thai pub that the returned highlight literally contains U+200B.)

---

## Concrete design starting point
1. After binding navigator: `navigator.addInputListener(DirectionalNavigationAdapter(navigator))` (LTR-correct edge-tap + arrow/space turns; tune threshold ~0.33). All input APIs are `@ExperimentalReadiumApi` — already opted in.
2. Register a SECOND custom `InputListener` AFTER it: `onTap` toggles chrome for center taps, returns `true`. `CompositeInputListener.onTap = listeners.any { it.onTap(event) }` runs in registration order and short-circuits, so edge taps hit the adapter first.
3. **DELETE** the full-size `pointerInput` Box (`ReaderChrome.kt:76-110`): 3-zone `detectTapGestures`, `onLongPress`, and `detectHorizontalDragGestures`. Remove `onLongPressRead` plumbing (`ReaderChrome.kt:64,83`; `ReaderScreen.kt:90`). Keep `AnimatedVisibility` TopAppBar + `chromeVisible`.
4. Long-press now flows natively → existing `selectionActionModeCallback` fires → extend its `onCreateActionMode` with Start-TTS/Copy/Translate/Add-Replacement → `currentSelection().locator` (+ `.text.highlight`, ZWSP-stripped for text actions) → `clearSelection()`.
5. Reuse `DecorableNavigator.applyDecorations` for replacement highlighting at `selection.locator`.

## Recommended gesture model
Drop the full-size Compose pointer overlay entirely and drive page-nav through Readium's own InputListener pipeline (option a). Register navigator.addInputListener(DirectionalNavigationAdapter(navigator)) for LTR-correct left/right edge-tap + arrow/space page turns, then register a SECOND custom InputListener whose onTap toggles chrome on center taps and returns true (CompositeInputListener runs listeners via any{} in registration order, so the adapter consumes edge taps first and only center taps reach yours). WHY: InputListener.onTap fires only AFTER the WebView's internal handling (links and, crucially, long-press text selection), so moving nav into InputListener never steals long-press — the native WebView selection + selectionActionModeCallback then fire unmodified, yielding the precise Locator+text+menu with ZERO bespoke coordinate math. This directly fixes the root cause: today the sibling overlay Box (higher z than the AndroidView) absorbs every pointer event including long-press, so the WebView never gets a selection. REGRESSION RISK: low and localized — (1) re-tune horizontalEdgeThresholdPercent to ~0.333 because the adapter uses EDGE zones (~30/40/30 default) not the repo's strict 33/33/33 thirds; (2) swipe page-turn is NOT a built-in InputListener event (onDrag is exposed but unwired) — either accept the paginated WebView's native LTR-correct horizontal paging or reimplement onDrag with the device-tuned 48dp threshold yourself. Reject (b) Compose pass-through (no-consume can't reach a sibling WebView at all; pointerInteropFilter{false} forces hand-rolled MotionEvent gesture discrimination = highest nav-regression risk). Hold (c) custom JS caretRangeFromPoint hit-test as a documented fallback ONLY if product needs a silent word-precise start without visible selection handles (budget 1-2 days; validate devicePixelRatio/scroll coordinate transform and Thai word-snapping on-device).

## Precision verdict
YES — a line/word-precise Locator AND the selected text are obtainable, via NATIVE WebView text selection (NOT a JS hit-test). Mechanism: SelectableNavigator.currentSelection() (suspend) returns Selection(locator: Locator, rect: RectF?). The selected text is at selection.locator.text.highlight (String?), NOT a Selection.text field (that field does not exist). GRANULARITY: word-level. Readium performs NO expansion — selection.js just reads window.getSelection()/getRangeAt(0). The Android System WebView selects the WORD under the finger on long-press and lets the user drag handles to extend the range; there is no sentence-level or caret default and no programmatic selectWordAt(PointF) API. There is also NO public point->Locator hit-test (TapEvent carries only PointF; firstVisibleElementLocator() is coarse top-of-screen). So precise selection is achievable ONLY through the native selection path, and word-vs-larger granularity is delegated to the OEM WebView (verify minor variation across the device matrix). A custom JS document.caretRangeFromPoint path could give silent word-precise targeting but needs a devicePixelRatio/scroll coordinate transform and Thai-aware word-snapping (Thai has no word boundaries) — fallback only.
