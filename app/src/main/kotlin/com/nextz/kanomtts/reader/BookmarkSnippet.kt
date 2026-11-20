package com.nextz.kanomtts.reader

import org.readium.r2.shared.publication.Locator

/** Max characters of cleaned snippet text retained before truncation. */
private const val MAX_SNIPPET = 100

/** Single-char horizontal ellipsis appended when text is truncated. */
private const val ELLIPSIS = "…"

/** U+200B zero-width space injected by the Thai word-break transform. */
private const val ZWSP = "​"

private val WHITESPACE = Regex("\\s+")

/**
 * Normalize a raw passage into a clean bookmark snippet:
 *  1. Strip ZWSP (U+200B) inserted by the Thai word-break display transform.
 *  2. Collapse every run of whitespace (incl. newlines/tabs) to a single ASCII space.
 *  3. Trim leading/trailing whitespace.
 *  4. Cap to [MAX_SNIPPET] chars; if truncated, append a single-char ellipsis.
 *
 * Pure and side-effect free so it can be unit-tested without Android/Readium.
 * Returns an empty string for blank input (the caller's fallback chain handles that).
 */
fun cleanSnippet(raw: String): String {
    val normalized = raw
        .replace(ZWSP, "")
        .replace(WHITESPACE, " ")
        .trim()
    return if (normalized.length > MAX_SNIPPET) {
        normalized.take(MAX_SNIPPET).trimEnd() + ELLIPSIS
    } else {
        normalized
    }
}

/**
 * Display-time label for a bookmark row.
 *
 * @property primary   bold first line — the passage excerpt, or a graceful fallback.
 * @property secondary muted second line — "อ่านแล้ว NN% · {chapter}" (empty if neither is known).
 */
data class BookmarkLabel(val primary: String, val secondary: String)

/**
 * Resolve the two-line bookmark label from the stored [snippet] and the decoded [locator]
 * (null when the stored JSON failed to decode).
 *
 * Primary fallback chain (legacy blank snippets degrade gracefully — no migration needed):
 *   1. snippet (the normalized excerpt) if non-blank,
 *   2. else the chapter/ToC title,
 *   3. else "ตำแหน่ง NN%" from totalProgression,
 *   4. else "ที่คั่นหน้า".
 *
 * Secondary line joins progress + chapter with " · ", omitting blanks. To avoid duplicating
 * whatever was promoted to the primary line, the matching part is dropped from the secondary.
 */
fun resolveBookmarkLabel(snippet: String, locator: Locator?): BookmarkLabel {
    val title = locator?.title?.let(::cleanSnippet)?.takeIf { it.isNotBlank() }
    val percentInt = locator?.locations?.totalProgression?.let { (it * 100).toInt() }

    val excerpt = snippet.takeIf { it.isNotBlank() }

    // Resolve primary + remember which fallback we used so we don't repeat it in secondary.
    // Note the percent phrasing differs by line: the primary fallback frames it as a position
    // ("ตำแหน่ง NN%"), while the secondary reads as progress ("อ่านแล้ว NN%").
    val primary: String
    var usedTitleAsPrimary = false
    var usedPercentAsPrimary = false
    when {
        excerpt != null -> primary = excerpt
        title != null -> { primary = title; usedTitleAsPrimary = true }
        percentInt != null -> { primary = "ตำแหน่ง $percentInt%"; usedPercentAsPrimary = true }
        else -> primary = "ที่คั่นหน้า"
    }

    val secondary = listOfNotNull(
        percentInt?.let { "อ่านแล้ว $it%" }.takeUnless { usedPercentAsPrimary },
        title.takeUnless { usedTitleAsPrimary },
    ).joinToString(" · ")

    return BookmarkLabel(primary = primary, secondary = secondary)
}
