# Reader Text Selection & Action Menu — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make long-press select a word and open a fully-custom action menu (Start TTS word-precise · Copy · Translate/Add-Replacement coming-soon) by moving reader navigation off the Compose overlay onto Readium's native `InputListener`.

**Architecture:** Delete the full-size Compose gesture overlay (it sits above the WebView and absorbs every touch, so native selection never starts). Drive page-nav through Readium's `InputListener` pipeline (`DirectionalNavigationAdapter` edge-taps + a custom center-tap/swipe listener). Because `InputListener.onTap` fires *after* the WebView's internal long-press handling, native text selection + `selectionActionModeCallback` then work unmodified — yielding the exact `Locator` + selected text with no coordinate math.

**Tech Stack:** Kotlin + Compose + Hilt, Readium 3.3.0 (`org.readium.r2.navigator.input.*`, `SelectableNavigator`, `DirectionalNavigationAdapter`), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-24-reader-selection-design.md`. **Verified API recon:** `docs/superpowers/spikes/2026-06-24-reader-selection-feasibility.md`.

## Global Constraints

- **Branch:** `feat/tts-playback` (so `master` receives the final word-precise reader together with Plan 07).
- All input/selection APIs are `@ExperimentalReadiumApi` — opt in (the reader files already do at class level).
- **Verified signatures (use verbatim):** `interface InputListener { fun onTap(event: TapEvent): Boolean = false; fun onDrag(event: DragEvent): Boolean = false; fun onKey(event: KeyEvent): Boolean = false }` in `org.readium.r2.navigator.input`; `data class TapEvent(val point: android.graphics.PointF)`; `data class DragEvent(val type: Type, val start: PointF, val offset: PointF)` + `enum class Type { Start, Move, End }`. `VisualNavigator.addInputListener(InputListener)` / `removeInputListener(InputListener)`. `org.readium.r2.navigator.util.DirectionalNavigationAdapter(navigator: OverflowableNavigator, …, horizontalEdgeThresholdPercent: Double? = 0.3, …)` — reads `navigator.publicationView.width` + `navigator.overflow.value.readingProgression`; returns `false` for center taps. `CompositeInputListener.onTap = listeners.any { it.onTap(event) }` (registration-order short-circuit). `SelectableNavigator.currentSelection(): Selection?` **(suspend)** / `clearSelection()`; `data class Selection(val locator: Locator, val rect: RectF?)` — **no `text` field**; selected text = `locator.text.highlight: String?`. `EpubNavigatorFragment` implements `SelectableNavigator` + `OverflowableNavigator` + `VisualNavigator`.
- **`selectionActionModeCallback`** is a plain `android.view.ActionMode.Callback`; the menu contains **only** what `onCreateActionMode` adds (Readium adds no system Copy/Select-All) — return `true`.
- **ZWSP:** the display pub carries U+200B (Thai word-break); `locator.text.highlight` includes it. Every text-consuming action (Copy now; Translate/Add-Replacement later) must `stripZwsp(...)` first. TTS Start-from-here is positional → unaffected.
- **Menu now:** Start TTS (word-precise) + Copy fully built; Translate + Add Replacement are menu items routed to a "เร็วๆ นี้" toast (no infra yet).
- Edge-tap zone matches the old 33/33/33 via `horizontalEdgeThresholdPercent = 0.333`.

---

## File Structure

- **Create** `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderGestures.kt` — pure `swipeDirection(...)` classifier.
- **Create** `app/src/main/kotlin/com/nextz/kanomtts/reader/TextSanitizer.kt` — pure `stripZwsp(...)`.
- **Create** `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderNavInputListener.kt` — custom `InputListener` (center-tap → chrome toggle; `onDrag` end → swipe page-turn).
- **Modify** `reader/ReaderViewModel.kt` — register listeners in `bindNavigator`; extend `selectionActionModeCallback` to four items; remove `startTtsFromHere`; inject `@ApplicationContext`.
- **Modify** `reader/ReaderChrome.kt` — delete the gesture `Box` + nav/long-press params; keep the `TopAppBar`.
- **Modify** `reader/ReaderScreen.kt` — drop the removed `ReaderChrome` params.
- **Modify** `app/src/main/res/values/ids.xml` (+ menu/toast strings) — add `copy_selection` / `translate_selection` / `add_replacement` ids (the `read_from_here` id already exists).
- **Tests:** `ReaderGesturesTest`, `TextSanitizerTest`.

---

## Task 1: Pure classifiers — `swipeDirection` + `stripZwsp` (TDD)

**Files:** Create `reader/ReaderGestures.kt`, `reader/TextSanitizer.kt`; Test `reader/ReaderGesturesTest.kt`, `reader/TextSanitizerTest.kt`.

**Interfaces:** Produces `enum class SwipeDir { Next, Prev }`; `fun swipeDirection(offsetX: Float, thresholdPx: Float): SwipeDir?`; `fun stripZwsp(text: String): String`.

- [ ] **Step 1: Failing tests** — `app/src/test/kotlin/com/nextz/kanomtts/reader/ReaderGesturesTest.kt`:
```kotlin
package com.nextz.kanomtts.reader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
class ReaderGesturesTest {
    @Test fun `finger left past threshold turns to next page`() {
        assertEquals(SwipeDir.Next, swipeDirection(offsetX = -60f, thresholdPx = 48f))
    }
    @Test fun `finger right past threshold turns to previous page`() {
        assertEquals(SwipeDir.Prev, swipeDirection(offsetX = 60f, thresholdPx = 48f))
    }
    @Test fun `below threshold is no turn`() {
        assertNull(swipeDirection(offsetX = -20f, thresholdPx = 48f))
        assertNull(swipeDirection(offsetX = 20f, thresholdPx = 48f))
    }
    @Test fun `exactly at threshold turns`() {
        assertEquals(SwipeDir.Next, swipeDirection(offsetX = -48f, thresholdPx = 48f))
        assertEquals(SwipeDir.Prev, swipeDirection(offsetX = 48f, thresholdPx = 48f))
    }
}
```
And `app/src/test/kotlin/com/nextz/kanomtts/reader/TextSanitizerTest.kt`:
```kotlin
package com.nextz.kanomtts.reader
import org.junit.Assert.assertEquals
import org.junit.Test
class TextSanitizerTest {
    @Test fun `strips zero-width spaces`() {
        assertEquals("กรุงเทพมหานคร", stripZwsp("กรุง​เทพ​มหานคร"))
    }
    @Test fun `leaves ordinary text and whitespace`() {
        assertEquals("hello world", stripZwsp("hello world"))
    }
    @Test fun `empty stays empty`() {
        assertEquals("", stripZwsp(""))
    }
}
```
- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests "*ReaderGesturesTest" --tests "*TextSanitizerTest"`.
- [ ] **Step 3: Implement** — `reader/ReaderGestures.kt`:
```kotlin
package com.nextz.kanomtts.reader
/** Page-turn direction from a horizontal drag offset. */
enum class SwipeDir { Next, Prev }
/**
 * Classify a finished horizontal drag. Finger moved LEFT (negative offset) past the
 * threshold → Next page; finger moved RIGHT (positive) past it → Prev; otherwise null.
 * Mirrors the Plan-06 device-tuned 48dp swipe.
 */
fun swipeDirection(offsetX: Float, thresholdPx: Float): SwipeDir? = when {
    offsetX <= -thresholdPx -> SwipeDir.Next
    offsetX >= thresholdPx -> SwipeDir.Prev
    else -> null
}
```
And `reader/TextSanitizer.kt`:
```kotlin
package com.nextz.kanomtts.reader
/** Remove U+200B (zero-width space) injected by ThaiZwspInjector before any text-consuming action. */
fun stripZwsp(text: String): String = text.replace("​", "")
```
- [ ] **Step 4: Run → PASS.** **Step 5: Commit** `feat(reader): swipeDirection + stripZwsp pure classifiers`.

---

## Task 2: Gesture re-architecture — Readium `InputListener` nav, remove the overlay

**Files:** Create `reader/ReaderNavInputListener.kt`; Modify `reader/ReaderViewModel.kt`, `reader/ReaderChrome.kt`, `reader/ReaderScreen.kt`.

**Interfaces:**
- Consumes: `swipeDirection` (Task 1); `ReaderViewModel.toggleChrome()`, `nextPage()`, `previousPage()` (existing).
- Produces: `class ReaderNavInputListener(onToggleChrome, onNextPage, onPrevPage, swipeThresholdPx: Float) : InputListener`.

- [ ] **Step 1: Custom listener** — `reader/ReaderNavInputListener.kt`:
```kotlin
package com.nextz.kanomtts.reader
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
/**
 * Reader nav handled by Readium after the WebView's own touch handling (so it never steals
 * long-press selection). Registered AFTER [DirectionalNavigationAdapter]: the adapter consumes
 * left/right EDGE taps, so any tap reaching here is a CENTER tap → toggle chrome. Swipe page-turn
 * is reimplemented from [DragEvent] (the device-tuned 48dp threshold).
 *
 * NOTE: the paginated WebView may also page horizontally on drag; if a swipe double-turns on
 * device, drop onDrag and rely on native paging (Task 4 device check).
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderNavInputListener(
    private val onToggleChrome: () -> Unit,
    private val onNextPage: () -> Unit,
    private val onPrevPage: () -> Unit,
    private val swipeThresholdPx: Float,
) : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        // Edge taps were consumed by DirectionalNavigationAdapter; a tap here is center.
        onToggleChrome()
        return true
    }
    override fun onDrag(event: DragEvent): Boolean {
        if (event.type != DragEvent.Type.End) return false
        return when (swipeDirection(event.offset.x, swipeThresholdPx)) {
            SwipeDir.Next -> { onNextPage(); true }
            SwipeDir.Prev -> { onPrevPage(); true }
            null -> false
        }
    }
}
```

- [ ] **Step 2: Register listeners in `ReaderViewModel.bindNavigator`.** Inside `bindNavigator(nav)` (after `navigator = nav`), register the two listeners on the navigator (which is a `VisualNavigator` + `OverflowableNavigator`). Compute the 48dp threshold from the application resources' display metrics (inject `@ApplicationContext context` — see Step 4):
```kotlin
// Page-nav via Readium input (replaces the deleted Compose overlay). DirectionalNavigationAdapter
// handles edge-tap + key page turns; the custom listener handles center-tap chrome-toggle + swipe.
val thresholdPx = 48f * context.resources.displayMetrics.density
val edgeAdapter = org.readium.r2.navigator.util.DirectionalNavigationAdapter(
    navigator = nav,
    horizontalEdgeThresholdPercent = 0.333,
)
val navListener = ReaderNavInputListener(
    onToggleChrome = ::toggleChrome,
    onNextPage = ::nextPage,
    onPrevPage = ::previousPage,
    swipeThresholdPx = thresholdPx,
)
nav.addInputListener(edgeAdapter)   // FIRST → consumes edge taps
nav.addInputListener(navListener)   // SECOND → center taps + drag reach here
this.edgeAdapter = edgeAdapter; this.navListener = navListener   // hold for removal
```
Add fields `private var edgeAdapter: org.readium.r2.navigator.util.DirectionalNavigationAdapter? = null` and `private var navListener: ReaderNavInputListener? = null`; in `onCleared()` (before nulling `navigator`) call `navigator?.let { it.removeInputListener(edgeAdapter ?: return@let); it.removeInputListener(navListener ?: return@let) }`. (If `DirectionalNavigationAdapter` lives in a different package than `org.readium.r2.navigator.util`, resolve it against the 3.3.0 source — recon spike §2 — and fix the import; it is `@ExperimentalReadiumApi` and takes the navigator as its first arg.)

- [ ] **Step 3: Strip the overlay from `ReaderChrome.kt`.** Delete the inner full-size gesture `Box` (the `pointerInput { detectTapGestures(...) }` + `pointerInput { detectHorizontalDragGestures(...) }`). Remove the now-unused params `onPrevPage`, `onNextPage`, `onToggleChrome`, `onLongPressRead`. Keep the `AnimatedVisibility { TopAppBar(...) }` and all its actions (back/ToC/settings/bookmarks/play-pause/debug-probe). Remove the now-unused imports (`detectTapGestures`, `detectHorizontalDragGestures`, `pointerInput`). Update both `@Preview`s to drop the removed params.

- [ ] **Step 4: Update `ReaderScreen.kt` + `ReaderViewModel` injection.** In `ReaderChromeLayer`, remove `onPrevPage`/`onNextPage`/`onToggleChrome`/`onLongPressRead` from the `ReaderChrome(...)` call. In `ReaderViewModel`, add `@ApplicationContext private val context: Context` to the constructor (Hilt provides it) — used for the swipe threshold (Step 2) and clipboard (Task 3). Delete `fun startTtsFromHere()` (superseded by the menu Start-TTS in Task 3). Keep `toggleChrome`/`nextPage`/`previousPage` (now called by the listener).

- [ ] **Step 5: Verify + commit.** `./gradlew :app:compileDebugKotlin :app:kspDebugKotlin :app:testDebugUnitTest :app:assembleDebug` → green (existing reader tests unaffected; nav is device-validated in Task 4). Commit `feat(reader): drive page-nav via Readium InputListener; remove Compose gesture overlay`.

---

## Task 3: Selection action menu — Start TTS · Copy · Translate/Add-Replacement (coming soon)

**Files:** Modify `reader/ReaderViewModel.kt`, `app/src/main/res/values/ids.xml`, `app/src/main/res/values/strings.xml`.

**Interfaces:** Consumes `stripZwsp` (Task 1), `ttsController.startAndPlay(bookId, locator)`, `(navigator as SelectableNavigator).currentSelection()/clearSelection()`, `context` (Task 2).

- [ ] **Step 1: Resource ids + strings.** In `res/values/ids.xml` add (keep the existing `read_from_here`):
```xml
<item name="copy_selection" type="id" />
<item name="translate_selection" type="id" />
<item name="add_replacement" type="id" />
```
In `res/values/strings.xml` add: `menu_start_tts`="อ่านจากตรงนี้", `menu_copy`="คัดลอก", `menu_translate`="แปล", `menu_add_replacement`="เพิ่มคำแทนที่", `toast_copied`="คัดลอกแล้ว", `toast_coming_soon`="เร็วๆ นี้".

- [ ] **Step 2: Extend `selectionActionModeCallback` in `ReaderViewModel.load()`.** Replace the single-item `onCreateActionMode` + `onActionItemClicked` (currently only `read_from_here`) with the four-item menu. Use string resources via `context.getString(...)`:
```kotlin
selectionActionModeCallback = object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        // Readium adds no system items — the menu is exactly these four.
        menu.add(0, R.id.read_from_here, 0, context.getString(R.string.menu_start_tts))
        menu.add(0, R.id.copy_selection, 1, context.getString(R.string.menu_copy))
        menu.add(0, R.id.translate_selection, 2, context.getString(R.string.menu_translate))
        menu.add(0, R.id.add_replacement, 3, context.getString(R.string.menu_add_replacement))
        return true
    }
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.read_from_here -> viewModelScope.launch {
                val loc = (navigator as? SelectableNavigator)?.currentSelection()?.locator
                    ?: return@launch
                ttsController.startAndPlay(bookId, loc)            // word-precise
                (navigator as? SelectableNavigator)?.clearSelection()
            }
            R.id.copy_selection -> viewModelScope.launch {
                val text = stripZwsp(
                    (navigator as? SelectableNavigator)?.currentSelection()?.locator?.text?.highlight.orEmpty()
                ).trim()
                if (text.isNotEmpty()) {
                    val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                    clip?.setPrimaryClip(android.content.ClipData.newPlainText("kanom", text))
                    toast(R.string.toast_copied)
                }
                (navigator as? SelectableNavigator)?.clearSelection()
            }
            R.id.translate_selection, R.id.add_replacement -> toast(R.string.toast_coming_soon)
            else -> return false
        }
        mode.finish()
        return true
    }
    override fun onDestroyActionMode(mode: ActionMode) {}
}
```
Add a private helper `private fun toast(resId: Int) { android.widget.Toast.makeText(context, resId, android.widget.Toast.LENGTH_SHORT).show() }` (Toast must run on main; `selectionActionModeCallback` fires on the UI thread, so a direct call is fine). Imports: `org.readium.r2.navigator.SelectableNavigator`, `android.view.{ActionMode, Menu, MenuItem}`.

- [ ] **Step 3: Verify + commit.** `./gradlew :app:compileDebugKotlin :app:kspDebugKotlin :app:assembleDebug :app:testDebugUnitTest` → green. The menu firing + actions are device-validated (Task 4). Commit `feat(reader): selection action menu — Start-TTS/Copy + Translate/Add-Replacement coming-soon`.

---

## Task 4: Device validation + finish

- [ ] **Step 1:** `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` → green.
- [ ] **Step 2: On-device (real device) selection + nav matrix:**
  - **Long-press a word** → native selection handles appear + the floating menu shows exactly the 4 items (no system Copy/Select-All); positions over the selection.
  - **อ่านจากตรงนี้** → TTS starts from the **pressed word** (word-precise), not the top of the page.
  - **คัดลอก** → clipboard has the word with **no zero-width spaces** (paste into a text field to confirm); "คัดลอกแล้ว" toast. (Confirms the ZWSP round-trip + strip.)
  - **แปล / เพิ่มคำแทนที่** → "เร็วๆ นี้" toast, no crash.
  - **Page-nav:** left-edge tap → previous, right-edge tap → next (feel matches the old 33/33/33); **center tap** → toggles chrome; **swipe** left→next / right→prev with the 48dp feel — and **verify NO double page-turn** (if the native paginated WebView also turns, drop `onDrag` from `ReaderNavInputListener` and rely on native paging).
  - Per-OEM long-press granularity (word vs more) — note any variation.
- [ ] **Step 3:** Resolve the swipe double-turn decision (keep `onDrag` vs native paging) from the device check; commit any adjustment.
- [ ] **Step 4:** This branch now carries Plan 07 + this capability — run the combined device matrix, then hand to `superpowers:finishing-a-development-branch` (merge `feat/tts-playback` → `master`).

---

## Self-Review

**Spec coverage:** §2 gesture re-arch → Task 2 (DirectionalNavigationAdapter + custom listener + overlay removal). §3 menu → Task 3 (4 items, word-precise Start-TTS, Copy). §4 ZWSP → Task 1 `stripZwsp` + Task 3 Copy. §5 components → Tasks 1–3 (all files). §6 testing → Task 1 (pure TDD) + Task 4 (device). §8 swipe/threshold/ZWSP-roundtrip open items → Task 2 (`onDrag` + `0.333`) + Task 4 (device decisions). All covered.

**Placeholder scan:** the "coming soon" toasts are decided behavior, not gaps. The `DirectionalNavigationAdapter` package note (Task 2 Step 2) is a recon-confirmed import to resolve against source, not a vague TODO. Device steps are explicit checklists.

**Type consistency:** `swipeDirection(offsetX, thresholdPx): SwipeDir?`, `stripZwsp(text): String`, `ReaderNavInputListener(onToggleChrome, onNextPage, onPrevPage, swipeThresholdPx)`, `SelectableNavigator.currentSelection()?.locator?.text?.highlight`, `ttsController.startAndPlay(bookId, locator)` are used consistently across Tasks 1–3 and match the verified Readium signatures + the existing controller API.
