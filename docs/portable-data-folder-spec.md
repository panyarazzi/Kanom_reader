# Portable Data Folder — Kanom

Branch: `feature/portable-data-folder-apk1`
Base: `feature/reader-improvements-apk2`

## Goal

Keep a portable copy of Kanom user data outside the app sandbox at
`/storage/emulated/0/Kanom` so uninstalling/reinstalling the APK does not
destroy the user's library metadata, reading positions, bookmarks, preferences,
voice configuration, or replacement rules.

## Existing data inventory

- Room database `kanom.db`, schema v2:
  - `books`: id, source URI, metadata, cover path, file size, timestamps
  - `progress`: Readium locator JSON, TTS locator JSON, percent, updatedAt
  - `bookmarks`: bookId, locator JSON, snippet, createdAt
  - `replacement_rules`: global and per-book replacement rules
  - `voice_config`: per-language provider, voice, speed, pitch
- Preferences DataStore `kanom_prefs`: reader and playback settings.
- Cover images currently live in the app's private files directory.
- EPUBs are currently referenced by persisted `content://` URIs. Persisted
  URI grants and private cover paths cannot be assumed to survive uninstall.

## Storage layout

```
Internal storage/
  Kanom/
    manifest.json
    data/
      kanom-backup.json
    covers/
      <book-id>.png
    backup/
      kanom-YYYYMMDD-HHmmss.zip
```

The app's live Room and DataStore remain in private app storage. The portable
folder contains a versioned export, not a live SQLite database.

## Backup payload

- All five Room tables, preserving stable IDs and Readium locator JSON exactly.
- All DataStore preference values, including settings added in later releases
  where supported by the versioned serializer.
- Cover images copied from private storage into `covers/`.
- A schema/version manifest and creation timestamp.
- EPUB payloads are not duplicated by default.

## Restore behavior

- Preview backup version and record counts before applying it.
- Restore Room tables transactionally; never leave a half-restored database.
- Restore preferences and covers.
- Reconcile source EPUB references. A restored `content://` URI is considered
  unverified until it can be opened; provide a relink/reselect path for missing
  sources. Do not show an inaccessible entry as a healthy book.
- Preserve the exact locator strings and percentages; do not reconstruct a
  reading position from percentage alone.
- Avoid duplicate records when the same backup is imported more than once.

## Access

The agreed target is a folder directly under internal storage root, not under
Documents, Download, or Android/data. On Android 11+, selecting the storage
root with the Storage Access Framework is restricted. The current sideloaded
APK route can use Android's special All files access settings flow, but the
permission must be clearly explained and checked before file operations. If
permission is denied, do not silently create a different folder; show a clear
unavailable state.

## Safety / integrity

- Write exports to a temporary file, flush, then atomically rename.
- Validate version, JSON structure, checksums/lengths where available before
  restore.
- Keep the previous backup until a new export succeeds.
- Never delete source EPUBs during backup or restore.
- Never overwrite current data without an explicit user confirmation.
- Report per-category counts and failures.

## Acceptance checks

1. Grant permission; app creates `Internal storage/Kanom` and its folders.
2. Export a library with multiple books, reading positions, bookmarks, settings,
   voice configs, and replacement rules.
3. Verify locator JSON and progress values round-trip exactly.
4. Uninstall and reinstall the app; reconnect/recognize the existing folder.
5. Restore and verify library metadata, settings, bookmarks, voice settings,
   and replacement rules.
6. Verify cover images display after restore.
7. Verify source EPUBs that remain accessible open correctly.
8. Verify missing/expired source URIs are clearly identified and can be relinked.
9. Import the same backup twice; no duplicates or corrupt records.
10. Deny All files access; app remains usable and explains why portable storage
    is unavailable.
