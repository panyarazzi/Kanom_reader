# Reader Text Selection & Action Menu — Design

- **Date:** 2026-06-24
- **Status:** Approved (design); ready for writing-plans
- **Feasibility recon (verified Readium 3.3.0 APIs):** `docs/superpowers/spikes/2026-06-24-reader-selection-feasibility.md`
- **Builds on:** Plan 06 (reader) + Plan 07 (TTS playback). Recommended branch: continue on `feat/tts-playback` so `master` receives the final word-precise reader in one coherent piece.

## 1. Goal & scope

Give the reader a **precise text-selection capability**: long-press a word → resolve it to (a) an exact Readium `Locator`, (b) the selected text, and (c) a **fully-custom action menu**. This is the foundation layer reused by current and future features:

- **In scope now:** the gesture re-architecture that makes native selection possible; the selection action menu with **Start TTS** (word-precise) + **Copy**; **Translate** and **Add Replacement** shown as menu items routed to a "coming soon" placeholder.
- **Out of scope (future plans that plug into this menu):** the translation service behind Translate; the replacement-rule editor behind Add Replacement; **Visual Replacement** (rendering replaced text in the reader via Decorations).

### The problem this fixes

The reader currently draws a **full-size Compose gesture overlay** (`ReaderChrome`'s inner `Box` with `detectTapGestures` + `detectHorizontalDragGestures`) **over** the `EpubNavigatorFragment`. That overlay is a Compose sibling at a higher z-index than the embedded `AndroidView`/WebView, so by Compose hit-test rules **the WebView never receives the pointer stream** — every touch, including long-press, is absorbed by the overlay. Native WebView text-selection therefore never starts, and the `selectionActionModeCallback` ("อ่านจากตรงนี้") is dead code for real selection. The page-precise overlay `onLongPress` shipped in Plan 07 (`startTtsFromHere`) is a band-aid that *structurally prevents* word-precision. There is no point→`Locator` hit-test in 3.3.0; **native selection is the only supported precise path.**

## 2. Gesture re-architecture (the foundation)

**Remove** the full-size Compose gesture overlay; **drive navigation through Readium's own `InputListener` pipeline** instead. The pivotal property (recon-verified): `InputListener.onTap` fires **after** the WebView's internal handling — including long-press selection — so moving nav into `InputListener` **never steals the long-press**, and native selection + the custom menu work unmodified, with no bespoke coordinate math.

On `bindNavigator(nav)`, register two listeners on the `VisualNavigator` (Composite runs them in registration order via `any{}`):

1. **`DirectionalNavigationAdapter(navigator)`** (Readium-provided `InputListener`): edge-tap left → previous page, edge-tap right → next page, plus arrow/space/page keys. Configure `horizontalEdgeThresholdPercent ≈ 0.333` so the left/right edge zones match the current 33/33/33 feel (default is ~30/40/30). It consumes edge taps; non-edge taps fall through.
2. **A custom `InputListener`** (`ReaderNavInputListener`) handling what the adapter does not:
   - `onTap(event)`: if `event.point.x` is in the **center** third → `toggleChrome()`, return `true`; else return `false`.
   - `onDrag(event)`: reimplement the device-tuned swipe — accumulate horizontal delta; on drag-end, **finger moved left ≥ 48dp → next page**, **finger moved right ≥ 48dp → previous page** (the exact behavior validated in Plan 06); return `true` when a page turn fires.

`ReaderChrome` shrinks to **just the `TopAppBar`** overlay (animated on `chromeVisible`) — it no longer owns any gesture `Box` or the `onPrevPage`/`onNextPage`/`onToggleChrome`/`onLongPressRead` callbacks. The play/pause action stays in the `TopAppBar`.

**Removed:** the overlay `Box` + its `pointerInput`s; `ReaderViewModel.startTtsFromHere()` and `ReaderChrome.onLongPressRead` (superseded by the menu's word-precise Start-TTS).

## 3. Long-press → word selection → action menu

Long-press selects the **word** under the finger (Android System WebView default; drag handles extend the range). The selection menu is built entirely by our `selectionActionModeCallback` — Readium adds **no** system Copy/Select-All (those appear only when the callback is `null`), so the menu contains exactly our items.

`onCreateActionMode` adds four items (return `true`); `onActionItemClicked` dispatches by id, then `mode.finish()`:

| Item (TH label) | Action |
|---|---|
| **อ่านจากตรงนี้** (Start TTS) | `val loc = (navigator as SelectableNavigator).currentSelection()?.locator ?: return`; `ttsController.startAndPlay(bookId, loc)`; `clearSelection()`. **Word-precise** — the locator is the selected word's position. |
| **คัดลอก** (Copy) | `val text = stripZwsp(currentSelection()?.locator?.text?.highlight.orEmpty())`; copy to `ClipboardManager`; toast "คัดลอกแล้ว"; `clearSelection()`. |
| **แปล** (Translate) | toast "เร็วๆ นี้" (coming soon) — no-op placeholder; wiring ready for the Translate plan. |
| **เพิ่มคำแทนที่** (Add Replacement) | toast "เร็วๆ นี้" (coming soon) — placeholder; wiring ready for the Replacement-editor plan. |

`currentSelection()` is **suspend** → the click handler launches on `viewModelScope`.

## 4. Data flow & the ZWSP caveat

- **Start TTS** uses `Selection.locator` **positionally** against the un-transformed TTS publication — unaffected by ZWSP.
- **Text-consuming actions** (Copy now; Translate/Add-Replacement later) read `selection.locator.text.highlight`. The display publication is wrapped by `ThaiZwspInjector` (U+200B at Thai word boundaries), and JS `\s` does not match U+200B, so **the highlight string carries U+200B**. Every text-consuming action must strip it first: `stripZwsp(s) = s.replace("​", "")`. (The existing `ReaderViewModel.cleanSnippet` already strips U+200B for bookmarks — extract/reuse it.)

## 5. Components & files

- **Create** `reader/ReaderNavInputListener.kt` — the custom `InputListener` (center-tap chrome-toggle + 48dp swipe via `onDrag`). Depends on pure classifiers (§6).
- **Create** `reader/ReaderGestures.kt` (or similar) — pure functions `tapZone(x: Float, width: Float): TapZone` (Left/Center/Right) and `swipeDirection(dx: Float, thresholdPx: Float): SwipeDir?` (Next/Prev/null). Unit-tested.
- **Create** `text/TextSanitizer.kt` (or reuse `cleanSnippet`) — `stripZwsp(text: String): String`. Unit-tested.
- **Modify** `reader/ReaderViewModel.kt` — register the listeners in `bindNavigator`; extend `selectionActionModeCallback` to the four items + actions; inject `ClipboardManager`/use `@ApplicationContext`; remove `startTtsFromHere`.
- **Modify** `reader/ReaderChrome.kt` — delete the gesture `Box` + its params; keep the `TopAppBar`.
- **Modify** `reader/ReaderScreen.kt` — drop the removed `ReaderChrome` gesture callbacks.
- **Strings** for the menu labels + toasts (TH).

## 6. Testing

- **Pure / TDD (JVM):** `tapZone(x, width)` (left/center/right boundaries at ~33%/66%), `swipeDirection(dx, threshold)` (finger-left→Next, finger-right→Prev, below-threshold→null), `stripZwsp` (removes U+200B, leaves other whitespace).
- **Device-validated (the recon's empirical risk → folds into Plan 07's device matrix):** native long-press selection handles + the floating ActionMode actually fire after the overlay is removed; the four-item menu positions correctly (`Callback2Wrapper.onGetContentRect`); edge-tap page-turn feel matches the old 33/33/33; the 48dp swipe feel; **ZWSP round-trip** (confirm `highlight` literally contains U+200B on a real Thai pub, then Copy yields clean text); per-OEM word-selection granularity variation; word-precise Start-TTS reads from the pressed word.

## 7. Verified APIs (from the feasibility recon)

- `EpubNavigatorFragment` implements `SelectableNavigator` (+ `DecorableNavigator`, `OverflowableNavigator`, `HyperlinkNavigator`).
- `SelectableNavigator.currentSelection(): Selection?` (suspend); `clearSelection()`. `data class Selection(val locator: Locator, val rect: RectF?)` — **no `text` field**; text = `locator.text.highlight: String?`.
- `VisualNavigator.addInputListener(InputListener)`; `InputListener.onTap(TapEvent): Boolean` / `onDrag(DragEvent): Boolean` / `onKey(KeyEvent): Boolean`; `TapEvent(point: PointF)`; `DragEvent` with start/offset points.
- `DirectionalNavigationAdapter` (Readium `InputListener` for edge-tap + key nav); `horizontalEdgeThresholdPercent`.
- `EpubNavigatorFragment.Configuration.selectionActionModeCallback: android.view.ActionMode.Callback?` — menu = exactly what `onCreateActionMode` adds; wrapped internally as `Callback2` for positioning.
- `DecorableNavigator.applyDecorations(decorations, group)` (suspend) — for future Visual Replacement at a selection locator.

## 8. Open items

- **Swipe vs native paging:** decided — reimplement the 48dp swipe via `onDrag` (not native paging).
- **Edge threshold tuning** (`horizontalEdgeThresholdPercent`) is a device-feel knob — start at 0.333, adjust on device.
- **ZWSP round-trip** is medium-confidence (JS-`\s`-not-matching-U+200B is certain; the live `highlight` content is unconfirmed) → a device check + the unconditional `stripZwsp`.
- Per-OEM long-press granularity variation → device matrix.
