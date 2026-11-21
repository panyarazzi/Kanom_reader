package com.nextz.kanomtts.data.progress

import java.util.Locale

/**
 * Shared display formatter for Readium totalProgression values.
 *
 * The persisted canonical value remains a fraction in the range 0.0..1.0.
 * This helper only formats that value; it never estimates progress or changes
 * the stored locator/progression.
 *
 * Returns null when progress is unavailable or outside the valid range so
 * callers can avoid displaying a fabricated percentage.
 */
fun formatProgressPercent(fraction: Double?): String? {
    val value = fraction ?: return null
    if (!value.isFinite() || value !in 0.0..1.0) return null
    return String.format(Locale.ROOT, "%.2f%%", value * 100.0)
}
