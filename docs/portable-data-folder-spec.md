# Portable Data Folder — Kanom

Branch: `feature/portable-data-folder-apk1`
Base: `feature/reader-improvements-apk2`

## Goal

Keep Kanom's recoverable user data outside the app sandbox at
`/storage/emulated/0/Kanom`, so uninstalling/reinstalling the APK does not
remove the library metadata, reading positions, bookmarks, reader settings,
voice configuration, replacement rules, or (when selected) portable EPUB
source copies.

## Existing data inventory

- Room database `kanom.db`, schema v2:
  - `books`: stable ID, source URI, metadata, cover path, file size, timestamps
  - `progress`: exact Readium locator JSON, TTS locator JSON, percent, updatedAt
  - `bookmarks`: stable IDs, bookId, locator JSON, snippet, createdAt
  - `replacement_rules`: global and per-book replacement rules
  - `voice_config`: per-language provider, voice, speed, pitch
- Preferences DataStore `kanom_prefs`: reader and playback settings.
- Cover images currently live in the app's private files directory.
- EPUBs are referenced by persisted `content://` URIs. Their persisted grants
  cannot be assumed to survive uninstall.

## On-device folder layout

```
Internal storage/
  Kanom/
    manifest.json
    backup/
    books/                 # created only when EPUB copying is explicitly enabled
      kanom-latest.zip
      kanom-<timestamp>.zip
```

The live Room database and DataStore remain in private app storage. Their
portable export (including settings and reading progress) is stored together in
`data.json` inside the ZIP; the folders `settings/`, `progress/`,
`bookmarks/`, and `covers/` are not created as empty placeholders. Cover
images are included inside the ZIP, never as a live SQLite file. The optional
EPUB copies are stored in `Kanom/books` only when explicitly enabled; copy the
entire `Kanom` folder when moving the library to another device.

## Backup payload

- All five Room tables, preserving stable IDs and Readium locator JSON exactly.
- DataStore preferences with their original primitive types. The local
  `premium_unlocked` entitlement flag is deliberately excluded.
- Cover images copied into the ZIP and restored to the app's private covers dir.
- A versioned JSON manifest and timestamp.
- EPUB source copies are disabled by default. Only when explicitly enabled,
  accessible source EPUBs are copied to `Kanom/books`; failed copies are counted
  in the result. The original EPUB files are never deleted.

## Restore behavior

- The user must confirm before current app data is replaced.
- Validate the ZIP entries, JSON structure, references, and format version.
- Restore all five Room tables inside one Room transaction.
- Restore DataStore preferences and cover images.
- If a portable EPUB copy exists, reconnect the book to its stable
  `Kanom/books/<book-id>.epub` path.
- If no EPUB copy exists, restore the saved source URI. If its old permission
  grant has expired, the user can select the same EPUB again from the library
  import action; matching title and file size relinks the existing stable book
  record instead of creating a new ID, preserving progress and bookmarks.
- Preserve exact locator JSON and percentage; never reconstruct the locator
  from percentage alone.
- Restoring the same backup again replaces the same records; it does not append
  duplicates.

## Access

The folder is directly under internal storage root, not inside Documents,
Download, or Android/data. On Android 11+, the app uses Android's special
All files access settings screen because SAF cannot select the storage root.
The app checks the permission before reading/writing and creates the folder
structure only after permission is granted. If permission is denied, the app
remains usable and portable backup actions stay unavailable.

This build is intended for sideloaded testing. All files access is a special
Android permission and is subject to Google Play policy if distribution moves
to Play Store.

## Integrity and safety

- Build the ZIP in a temporary file and atomically replace the latest archive
  only after the export completes.
- Keep one current archive, `backup/kanom-latest.zip`, to avoid duplicating
  the same backup payload in a timestamped copy.
- Never delete source EPUBs during backup or restore.
- Restore database tables transactionally and ask before replacing user data.
- Report record totals and EPUB copy successes/failures.

## Acceptance checks

1. Grant All files access; app creates `Internal storage/Kanom` and subfolders.
2. Back up multiple books with reading locators, TTS locators, bookmarks,
   settings, voice configs, and global/per-book replacement rules.
3. Verify exact locator strings and percentages round-trip.
4. Verify covers are restored and display after app reinstall.
5. With EPUB copying enabled, verify copied files remain in `Kanom/books`,
   and that the app opens them after reinstall/restore.
6. With EPUB copying disabled, reselect a source EPUB and verify the existing
   stable book ID, progress, and bookmarks are retained.
7. Copy the entire `Kanom` folder to another device, install Kanom, grant
   access, restore, and verify data and portable EPUB paths.
8. Import the same backup twice; verify no duplicates or corrupt records.
9. Deny All files access; verify the rest of the app remains usable and the
   portable feature explains why it is unavailable.
