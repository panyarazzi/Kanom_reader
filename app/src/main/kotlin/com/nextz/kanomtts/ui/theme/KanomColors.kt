package com.nextz.kanomtts.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Brand colors not covered by Material's ColorScheme. */
data class KanomColors(
    val highlight: Color,
    val highlightContainer: Color,
    val onHighlight: Color,
    val primaryGradientEnd: Color,
    val muted: Color,
)

val LightKanomColors = KanomColors(
    highlight = RoseHighlight,
    highlightContainer = RoseHighlightBg,
    onHighlight = RoseHighlightInk,
    primaryGradientEnd = AnchanGradientEnd,
    muted = Muted,
)

val DarkKanomColors = LightKanomColors.copy(
    highlightContainer = Color(0xFF5A2740),
    onHighlight = Color(0xFFFFD4E6),
    muted = Color(0xFF9A93A6),
)

val LocalKanomColors = staticCompositionLocalOf { LightKanomColors }

/** Reading surface themes (spec §5.1). Pure data — JVM testable. */
enum class ReaderTheme { LIGHT, SEPIA, DARK, TRUE_BLACK }

fun ReaderTheme.surface(): Color = when (this) {
    ReaderTheme.LIGHT -> CoconutCream
    ReaderTheme.SEPIA -> SepiaBg
    ReaderTheme.DARK -> DarkBg
    ReaderTheme.TRUE_BLACK -> TrueBlack
}

fun ReaderTheme.onSurface(): Color = when (this) {
    ReaderTheme.LIGHT -> Ink
    ReaderTheme.SEPIA -> SepiaInk
    ReaderTheme.DARK -> DarkInk
    ReaderTheme.TRUE_BLACK -> DarkInk
}
