package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.ui.theme.ReaderTheme

enum class ReadingFont { SARABUN, SERIF, SYSTEM }
enum class ReadingUnit { SENTENCE, PARAGRAPH }

data class KanomPreferences(
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    val readingFont: ReadingFont = ReadingFont.SARABUN,
    val fontSizeSp: Int = 18,
    /** Shared font size for persistent top/footer reading progress labels. */
    val progressFontSizeSp: Int = 24,
    val topProgressEnabled: Boolean = true,
    val footerProgressEnabled: Boolean = true,
    val topProgressLeft: String = "CHAPTER_PAGE",
    val topProgressCenter: String = "CHAPTER_TITLE",
    val topProgressRight: String = "BOOK_PERCENT",
    val footerProgressLeft: String = "BOOK_PERCENT",
    val footerProgressCenter: String = "CHAPTER_TITLE",
    val footerProgressRight: String = "TIME_BATTERY",
    val lineHeight: Float = 1.6f,
    /** First-line paragraph indent in em units. */
    val paragraphIndentEm: Float = 1.0f,
    /** Extra paragraph spacing in em units. */
    val paragraphSpacingEm: Float = 0.0f,
    /** Horizontal page-gutter factor, retained for compatibility with existing DataStore values. */
    val marginDp: Int = 16,
    val topMarginDp: Int = 0,
    val bottomMarginDp: Int = 0,
    val boldText: Boolean = false,
    /** When true, swap the page-turn direction assigned to volume keys. */
    val reverseVolumeKeys: Boolean = false,
    /** When enabled, every unhandled tap outside the reserved center-menu zone advances. */
    val tapAnywhereToNext: Boolean = false,
    /** Previous/Next touch-zone ratio; Next is always the inverse to total 100%. */
    val previousTouchZonePercent: Int = 50,
    val skipUrls: Boolean = false,
    val highlightOn: Boolean = true,
    val autoScroll: Boolean = true,
    val sleepTimerDefaultMin: Int = 30,
    val premiumUnlocked: Boolean = false,
    // Reader display controls.
    val usePublisherStyles: Boolean = false,
    val forceFont: Boolean = true,
    val stripColors: Boolean = false,
    val thaiWordBreak: Boolean = true,
    val pageAnimation: Boolean = true,
    val visualReplacement: Boolean = false,
    val readingUnit: ReadingUnit = ReadingUnit.SENTENCE,
) {
    val nextTouchZonePercent: Int get() = 100 - previousTouchZonePercent
}
