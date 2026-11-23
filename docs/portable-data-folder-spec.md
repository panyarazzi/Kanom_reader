# Portable Data Folder — Kanom

## Goal

Keep Kanom's recoverable user data outside the app sandbox at
`/storage/emulated/0/Kanom`, so uninstalling/reinstalling the APK does not
remove library metadata, reading positions, bookmarks, reading statistics,
tags, notes, reader settings, voice configuration, or replacement rules.

**EPUB source files are never copied, moved, renamed, extracted, or deleted by
Backup / Restore.** The app stores only metadata and the original source URI.
If that URI permission expires after reinstall or a move, the user explicitly
relinks the existing book to the original EPUB.

## Existing data inventory

- Room database `kanom.db`, schema v6:
  - `books`: stable ID, source URI, metadata, cover path, file size,
    SHA-256 fingerprint, original source filename, source-folder path
  - `progress`: exact Readium locator JSON, TTS locator JSON, percent, updatedAt
  - `bookmarks`: stable IDs, bookId, locator JSON, snippet, createdAt
  - `replacement_rules`: global and per-book replacement rules
  - `voice_config`: per-language provider, voice, speed, pitch
  - `reading_sessions`: persisted reading-time segments for statistics
  - `book_tags`: per-book tags
  - `book_notes`: per-book notes/reviews
- Preferences DataStore `kanom_prefs`: reader and playback settings, including
  custom typography and tap-to-turn behavior.
- Cover images live in the app's private files directory.
- EPUBs are referenced by persisted `content://` URIs. Their persisted grants
  cannot be assumed to survive uninstall.

## On-device folder layout

```
Internal storage/
  Kanom/
    manifest.json
    backup/
      kanom-latest.zip
```

The live Room database and DataStore remain in private app storage. Their
portable export (including settings, reading statistics, tags, notes, folder
metadata, and reading progress) is stored together in `data.json` inside the
ZIP. Cover images are included inside the ZIP and restored to the app's private
covers directory. There is no `Kanom/books` directory and no EPUB payload.

## Backup payload

- All eight Room tables, preserving stable IDs and Readium locator JSON exactly.
- Book SHA-256 fingerprints, original source filenames, and source-folder paths.
- DataStore preferences with their original primitive types. The local
  `premium_unlocked` entitlement flag is deliberately excluded.
- Cover images copied into the ZIP and restored to the app's private covers dir.
- A versioned JSON manifest and timestamp.
- Reading-session records so reading statistics survive reinstall.
- Tags and notes/reviews so user annotations survive reinstall.

**EPUB source bytes are never included in a new backup.** The backup operation
does not open EPUBs for copying and does not create EPUB copy counters or an
`includeEpubFiles` option.

## Restore behavior

- The user must confirm before current app data is replaced.
- Validate the ZIP metadata entries, JSON structure, references, and format
  version.
- Restore all Room tables inside one Room transaction.
- Restore DataStore preferences and cover images.
- Only `data.json` and `covers/` entries are restored. Unknown ZIP entries are
  ignored. This intentionally supports legacy archives that may contain
  `portableEpub` pointers or `Kanom/books` EPUB payloads: those EPUB entries
  are never extracted or copied.
- Restore the saved original source URI. If its permission has expired, the
  reader error screen offers explicit file or folder selection to relink.
- Folder relinking searches recursively. SHA-256 is authoritative when the
  saved fingerprint exists; legacy records fall back to metadata/title and file
  size. Every candidate is shown for explicit user selection. Ambiguous matches
  are never selected automatically.
- Relinking updates only the existing book's source URI and source metadata
  after validation. It preserves Book ID, progress, bookmarks, tags, notes,
  cover, and reading statistics. It never copies, moves, renames, or deletes an
  EPUB.
- Preserve exact locator JSON and percentage; never reconstruct the locator
  from percentage alone.
- Restoring the same backup again replaces the same records; it does not append
  duplicates.

## Access

The folder is directly under internal storage root, not inside Documents,
Download, or Android/data. On Android 11+, the app uses Android's special
All files access settings screen because SAF cannot select the storage root.
The app checks permission before reading/writing and creates the folder
structure only after permission is granted. If permission is denied, the app
remains usable and portable backup actions stay unavailable.

This build is intended for sideloaded testing. All files access is a special
Android permission and is subject to Google Play policy if distribution moves
to Play Store.

## Integrity and safety

- Build the ZIP in a temporary file and atomically replace the latest archive
  only after the export completes.
- Keep one current archive, `backup/kanom-latest.zip`, to avoid duplicating
  the same backup payload in timestamped copies.
- Never copy, extract, move, rename, or delete source EPUBs during backup,
  restore, folder import, or relinking.
- Restore database tables transactionally and ask before replacing user data.
- Report record totals for books, progress, bookmarks, rules, voices, tags,
  notes, and reading sessions.

## Acceptance checks

1. Grant All files access; app creates `Internal storage/Kanom/backup`.
2. Back up multiple books with reading locators, TTS locators, bookmarks,
   settings, voice configs, global/per-book replacement rules, tags, notes,
   reading sessions, SHA-256 values, and folder/source filename metadata.
3. Verify exact locator strings and percentages round-trip.
4. Verify covers are restored and display after app reinstall.
5. Inspect the ZIP: it contains `data.json` and cover entries, but no EPUB
   entries or `Kanom/books` payload.
6. Restore a legacy ZIP containing `portableEpub` or `Kanom/books` entries;
   verify EPUB entries are ignored and never extracted.
7. After reinstall, verify an inaccessible original URI shows file/folder
   relinking. Verify SHA-256 matches first and legacy title/size fallback works.
8. Put multiple matching candidates in a selected folder; verify all are shown
   and none is selected automatically.
9. Confirm a candidate; verify the original stable Book ID, progress,
   bookmarks, tags, notes, and reading statistics remain unchanged.
10. Copy the entire `Kanom` folder to another device, install Kanom, grant
    access, restore, and verify metadata and settings. Relink source EPUBs only
    when the original URI permission is unavailable.
11. Import the same backup twice; verify no duplicates or corrupt records.
12. Deny All files access; verify the rest of the app remains usable and the
    portable feature explains why it is unavailable.

## First-launch automatic recovery

On the first launch after a fresh install, the startup gate requests All files
access before entering the library. After permission is granted, it checks
`Kanom/backup/kanom-latest.zip` and invokes the existing restore operation
automatically when a valid backup is present. The user does not need to open
Settings or press Restore.

The startup markers are stored under Android's `noBackupFilesDir`: they
survive app updates but are not copied back by Android Auto Backup after a
reinstall. The gate distinguishes a fresh install from an in-place update using
package installation timestamps, then persists a pending-recovery marker before
asking for permission. This prevents Android-restored private data from
suppressing the newer portable backup. An interrupted restore is retried on
the next launch. A failed restore is reported with retry and continue actions;
it must not silently enter the library as though recovery succeeded.

For app updates, the gate checks for existing local Room or DataStore user data
before attempting recovery. Existing installations are allowed into the app
without restoring an older portable backup over their current data. If no
backup exists after permission is granted, the app completes first-run setup
and opens the library normally.

The notification permission request is deferred until the storage/recovery
flow is complete so that it cannot interrupt the first-launch permission step.

Additional acceptance checks:

13. Uninstall and reinstall while keeping `Internal storage/Kanom`; first
    launch asks for storage access, then restores automatically without
    pressing Restore.
14. Upgrade an installation that already contains local books or preferences;
    verify startup does not overwrite current data with the portable ZIP.
15. Kill the app during automatic restore and relaunch; verify interrupted
    restore is retried and completes.
16. Use a corrupt or unsupported ZIP; verify startup reports failure and offers
    retry or continue, without claiming success.
17. Grant access when no backup exists; verify the app proceeds to the library
    without getting stuck in the startup gate.


## Restore-time source EPUB reconciliation (required before Release)

After reading and validating the backup metadata, Restore must reconcile every
backed-up book against the user's original EPUB folder before replacing the
current database. Do not wait until the user opens a book to discover that its
persisted URI is no longer accessible.

1. Ask the user to select the root folder that contains their original EPUB
   library. Scan recursively through EPUB files in that folder and descendants.
   Persist the SAF tree permission so the selected source folder can be reused
   where Android permits it.
2. Match backed-up books against discovered EPUBs:
   - If a saved SHA-256 exists, it is authoritative. Compute candidate hashes
     and match by exact SHA-256.
   - For legacy backups without SHA-256, use original source filename, EPUB
     metadata title, and file size as fallback evidence. Do not silently claim
     a match based on a weak or ambiguous comparison.
3. Put books with exactly one verified match into a single review list. Show
   every book and its proposed source file together, for example:
   - Book A -> A.epub
   - Book B -> B.epub
   - Book C -> C.epub
   The user reviews this complete list and confirms all unique matches once.
4. Put books with multiple plausible matches into a separate ambiguity queue.
   Show one book at a time with every candidate filename/path and enough
   distinguishing information to choose exactly one. Include an explicit
   "Skip this book" action. Never preselect or silently choose one candidate.
5. Put books with no match into a separate not-found list. Explain that these
   books will retain their metadata and reading records but their source URI
   will remain unresolved; allow the user to continue and relink them later.
6. After the user confirms the unique-match batch and resolves or skips every
   ambiguous/not-found book, apply the selected URI mappings to the restored
   Book records and continue the restore. The restore must remain retryable if
   the user cancels or the app is interrupted before the final commit.
7. Validate each selected URI before commit. Preserve stable Book ID, progress,
   exact Readium locator JSON, TTS locator, bookmarks, tags, notes, cover,
   statistics, and book-specific replacement rules. Update only source URI and
   source filename/folder metadata plus a verified SHA-256 where appropriate.
8. Never copy, move, rename, extract, or delete EPUB files. Do not import
   relinked books as new records and do not create duplicate Book IDs.

### Restore-time reconciliation acceptance checks

18. Restore a backup with several books whose original URI permissions have
    expired. The app asks once for the root source folder and scans nested
    folders automatically.
19. When several books each have exactly one verified match, show the complete
    mapping list and apply it after one batch confirmation.
20. When a book has multiple matches, show all candidates separately, require
    one explicit choice, and offer "Skip this book". No candidate is
    preselected.
21. When a book has no match, list it separately and allow Restore to continue
    with its source unresolved for later relinking.
22. Cancel during reconciliation or interrupt the app; verify the current
    database remains intact and Restore can be resumed safely.
23. After completion, verify every matched book opens and retains its original
    Book ID, locator/progress, TTS locator, bookmarks, tags, notes, statistics,
    cover, and per-book replacement rules.
24. Verify EPUB file paths, bytes, names, and folder contents are unchanged.
