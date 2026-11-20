package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.ui.theme.ReaderTheme

enum class ReadingFont { SARABUN, SERIF, SYSTEM }
enum class ReadingUnit { SENTENCE, PARAGRAPH }

data class KanomPreferences(
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    val readingFont: ReadingFont = ReadingFont.SARABUN,
    val fontSizeSp: Int = 18,
    val lineHeight: Float = 1.6f,
    val marginDp: Int = 16,
    val skipUrls: Boolean = false,
    val highlightOn: Boolean = true,
    val autoScroll: Boolean = true,
    val sleepTimerDefaultMin: Int = 30,
    val premiumUnlocked: Boolean = false,
    // Task 6 escape hatches
    val usePublisherStyles: Boolean = false,
    val forceFont: Boolean = true,
    val stripColors: Boolean = false,
    val thaiWordBreak: Boolean = true,
    // Page-flip animation toggle (true = animate, false = instant)
    val pageAnimation: Boolean = true,
    val visualReplacement: Boolean = false,
    val readingUnit: ReadingUnit = ReadingUnit.SENTENCE,
)
