package com.nextz.kanomtts.reader

/**
 * Wider ToC panel on large e-reader displays (G7S class and above).
 * Compact phones retain the existing wider fraction so chapter titles remain readable.
 */
internal fun readerTocWidthFraction(
    model: String,
    manufacturer: String,
    screenWidthDp: Int = 0,
): Float =
    if (
        model.contains("G7S", ignoreCase = true) ||
        manufacturer.contains("Meebook", ignoreCase = true) ||
        screenWidthDp >= 600
    ) 0.575f else 0.82f
