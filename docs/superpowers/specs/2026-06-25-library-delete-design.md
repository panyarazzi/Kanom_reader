# Library Delete — Design Spec

_Written 2026-06-25. Standalone feature (the long-standing backlog item), after the Replacement work (09a/09b) merged._

Parent design: `docs/superpowers/specs/2026-06-23-kanom-tts-design.md` §5.1 (library management).

---

## 1. Goal

Let the user remove books from the library: **long-press a book → multi-select → bulk delete**, with a correct cleanup cascade (no Room FK cascade exists, so every dependent must be handled explicitly).

There is no delete in the UI today; `BookDao.delete(id)` / `BookRepository.remove(id)` exist but are unwired, and `LibraryViewModel`/`LibraryScreen` have no delete affordance.

### Locked decisions (brainstorming 2026-06-25)
- **UX:** long-press → selection mode (checkbox overlays + a selection bar with count/trash/close); tap toggles selection while in mode; trash → a confirm dialog → delete.
- **Cascade includes the book's per-book replacement rules** (`scope='book' AND bookId`) — they're dead once the book is gone. Global rules untouched.
- **No undo** — the confirm dialog is the safeguard; delete is then immediate and final.
- The EPUB is a SAF `content://` URI the app never copied — delete removes the book from the library and **releases the persistable URI permission**; it does NOT delete the user's actual file.

### Out of scope
Trash/restore, archive, cloud sync, undo.

---

## 2. Existing seams (verified by recon 2026-06-25)

- `BookDao.delete(id)` (`DELETE FROM books WHERE id = :id`); `BookRepository.remove(id) = dao.delete(id)`. `BookReader.get(id): BookEntity?`. `BookWriter` has only `isDuplicate`+`add` (no delete contract).
- `BookEntity(id, uri, title, author, lang, coverPath, fileSize, addedAt, lastOpenedAt)`. Cover written by `EpubImporter` to `File(coversDir, "$id.png")` where `id = stableId(uri) = sha256(uri)`; `coverPath` is that absolute path.
- **SAF:** import calls `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)` (LibraryScreen launcher + MainActivity intent, both SecurityException-tolerant). Release counterpart: `contentResolver.releasePersistableUriPermission(Uri.parse(book.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)`.
- **Orphan rows (no delete-by-book today):** `ProgressDao` (no delete query at all), `BookmarkDao` (only `delete(id: Long)`), `ReplacementRuleDao` (only `delete(id: Long)`; book rules are `scope='book' AND bookId=:bookId`). All three need a new `deleteByBook` query.
- **Context:** `@ApplicationContext Context` injected the `ImportViewModel` way; `context.contentResolver` for the SAF release.
- **TTS stop:** `TtsPlaybackController` (`@Singleton`) exposes `state: StateFlow<PlaybackState>` and `fun stop()`; `PlaybackState.Playing(bookId, locator)` / `Paused(bookId, locator)`. Injectable like `PlaybackViewModel` does.
- **Library UI:** `LibraryViewModel(@HiltViewModel; BookReader, ProgressReader)`; `uiState` is `combine(booksWithProgress, _viewMode, _query)`; actions `setViewMode`/`setQuery`. `LibraryUiState(books: List<BookCard>, continueCard, viewMode, query)`; `BookCard(id, title, author, coverPath, percent, isReading)`. `LibraryScreen` has its own `Scaffold` (FAB + no TopAppBar); `LibraryContent` holds the search row + grid/list; `BookGridItem`/`BookListItem` use `.clickable(onClick)`. Hosted as the `LIBRARY` bottom-nav tab in `KanomApp`.

---

## 3. Architecture

### 3.1 `BookDeleter` seam — the cascade (one book)
A testable `interface BookDeleter { suspend fun delete(bookId: String) }`, implemented by `DefaultBookDeleter` (`@Inject`: `BookDao`, `ProgressDao`, `BookmarkDao`, `ReplacementRuleDao`, `@ApplicationContext Context`). For `bookId`:
1. `val book = bookDao.get(bookId) ?: return` (nothing to do if already gone).
2. **Release SAF permission:** `runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(book.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }` (tolerate `SecurityException` / providers that never granted one).
3. **Delete cover file:** `book.coverPath?.let { runCatching { File(it).delete() } }`.
4. **Delete orphan rows:** `progressDao.deleteByBook(bookId)`, `bookmarkDao.deleteByBook(bookId)`, `replacementRuleDao.deleteByBook(bookId)`.
5. **Delete book row:** `bookDao.delete(bookId)`.

It does NOT touch TTS (kept out so the seam stays free of the heavy controller and is unit-testable); the ViewModel handles the playing-book stop. `BookDeleter` is bound in a Hilt module (`@Binds DefaultBookDeleter → BookDeleter`). `BookDao.get(id)` exists via `BookReader`; if `BookDao` lacks a direct `get`, inject `BookReader` for the lookup instead — confirm at implementation.

### 3.2 New DAO queries
- `ProgressDao`: `@Query("DELETE FROM progress WHERE bookId = :bookId") suspend fun deleteByBook(bookId: String)`
- `BookmarkDao`: `@Query("DELETE FROM bookmarks WHERE bookId = :bookId") suspend fun deleteByBook(bookId: String)`
- `ReplacementRuleDao`: `@Query("DELETE FROM replacement_rules WHERE scope = 'book' AND bookId = :bookId") suspend fun deleteByBook(bookId: String)`

(No Room migration — adding queries doesn't change schema.)

### 3.3 Selection state + actions (`LibraryViewModel`)
- `LibraryUiState` gains `selectedIds: Set<String> = emptySet()`; derived `val isSelectionMode get() = selectedIds.isNotEmpty()`.
- `LibraryViewModel` adds `private val _selectedIds = MutableStateFlow(emptySet<String>())`, folds it into the `combine` for `uiState`, and exposes:
  - `toggleSelection(id)` — add/remove from `_selectedIds` (pure set logic).
  - `clearSelection()` — `_selectedIds.value = emptySet()`.
  - `deleteSelected()` — (a) read `controller.state.value`; if its `bookId` ∈ the selected set, `controller.stop()`; (b) for each selected id, `bookDeleter.delete(id)`; (c) `clearSelection()`.
- Injects `BookDeleter` + `TtsPlaybackController` (the `@Singleton`, like `PlaybackViewModel`).
- Selection survives the library's reactive `books` updates (the deleted ids simply drop out of `books`; `selectedIds` is cleared after delete).

### 3.4 UI (`LibraryScreen` / `LibraryContent` / `LibraryComponents`)
- **Selection bar:** when `isSelectionMode`, replace the search row with a bar: a **×** (calls `clearSelection`), the count "เลือก N เล่ม", and a **trash** `IconButton` (opens the confirm dialog). The **import FAB is hidden** in selection mode.
- **Grid/list items:** `BookGridItem`/`BookListItem` swap `.clickable(onClick)` for `.combinedClickable(onClick = { if (isSelectionMode) onToggle(id) else onOpen(id) }, onLongClick = { onToggle(id) })`, and overlay a `Checkbox` (or a check badge on the cover corner) + a selected tint when `card.id in selectedIds`. New params threaded down: `isSelectionMode`, `selectedIds`, `onToggle`.
- **Confirm dialog:** an `AlertDialog` ("ลบ N เล่ม?", ลบ / ยกเลิก) → on confirm, `viewModel.deleteSelected()`.
- **Back handling:** `BackHandler(enabled = isSelectionMode) { clearSelection() }` so the system back exits selection mode rather than the tab.

### 3.5 Edge cases
- Deleting the **continue-listening** book: `continueCard` is derived from `books`, so it recomputes automatically.
- A deleted book whose **reader is in the back stack**: on returning, `source.book(bookId)` returns null → the existing `ReaderUiState.Error("Book not found")` path (graceful, no crash).
- SAF release on a `file://` or non-persistable URI → `SecurityException` swallowed (step 2's `runCatching`).
- Selecting then a reactive refresh removes a book → the dropped id is harmless in `selectedIds` (deleteSelected no-ops on a missing book via step 1's `?: return`).

---

## 4. Data flow
```
long-press book ─→ toggleSelection(id) ─→ selectedIds ─→ uiState.isSelectionMode → selection bar + checkboxes
trash ─→ confirm dialog ─→ deleteSelected():
   ├─ controller.state.bookId ∈ selectedIds ? → controller.stop()  (clears mini-player)
   ├─ for each id → BookDeleter.delete(id): releaseSAF · delete cover file · deleteByBook(progress,bookmark,book-rules) · delete book row
   └─ clearSelection()
books Flow re-emits without the deleted rows → grid updates
```

## 5. Error handling
- Each external side effect (SAF release, file delete) is wrapped in `runCatching` so one failure can't abort the cascade or crash; the DB deletes are awaited.
- `delete(bookId)` no-ops if the book is already gone (step 1 guard).
- Confirm dialog is the only safeguard (no undo).

## 6. Testing strategy
- **Pure (TDD):** selection-set toggle (add then remove returns to empty; toggling distinct ids accumulates); the "is a playing book among the selected" predicate (`PlaybackState.Playing/Paused.bookId ∈ selected`).
- **`BookDeleter` (Robolectric + in-memory Room):** seed a book + a progress row + a bookmark + a global rule + a book-scoped rule + a temp cover file; `delete(bookId)`; assert the book/progress/bookmark/book-rule rows are gone, the **global rule survives**, the cover file is deleted, and (via Robolectric's `ShadowContentResolver.getPersistedUriPermissions`) the persistable permission is released. A missing-book `delete` is a no-op.
- **`LibraryViewModel` (Robolectric + in-memory Room/fakes, `Dispatchers.setMain` pattern):** `toggleSelection`/`clearSelection` update `uiState.selectedIds`; `deleteSelected` calls a fake `BookDeleter` for each id, stops a fake controller only when a selected id is playing, and clears selection.
- **Device matrix:** long-press enters selection mode; multi-select + trash + confirm deletes the books (gone from grid); the cover thumbnails disappear; deleting the currently-playing book stops TTS + hides the mini-player; the continue-hero updates; back exits selection mode.

## 7. Build sequence (hint for writing-plans)
1. **`deleteByBook` DAO queries** (Progress/Bookmark/ReplacementRule) + Robolectric Room tests.
2. **`BookDeleter` seam** + `DefaultBookDeleter` cascade + Hilt binding + Robolectric test (incl. SAF release + cover-file delete + global-rule-survives).
3. **`LibraryViewModel`** selection state + `toggleSelection`/`clearSelection`/`deleteSelected` (TTS-stop) + tests.
4. **UI** — selection bar + checkbox overlay + `combinedClickable` + confirm dialog + `BackHandler` + hide-FAB-in-selection (LibraryScreen/Content/Components).
5. Opus whole-branch review; ledger; `finishing-a-development-branch` (device sign-off gate).

## 8. Open items / risks
- **`combinedClickable` is experimental Compose foundation** — needs `@OptIn(ExperimentalFoundationApi::class)` (already used elsewhere? confirm). Low risk.
- **Cover path vs file:** delete uses `book.coverPath` (absolute path). Confirm it's the `File(coversDir,"$id.png").absolutePath` written at import (recon confirms it is).
- **SAF release on Robolectric:** `ShadowContentResolver` supports `release/takePersistableUriPermission` + `getPersistedUriPermissions` — the test asserts release; on a real device, a never-persisted URI throws `SecurityException` (swallowed).
- **Selection persistence across process death:** `selectedIds` is in-memory (not `SavedStateHandle`) — a process death mid-selection drops the selection. Acceptable (transient UI state).
