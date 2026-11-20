package com.nextz.kanomtts.reader.engine

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.toArgb
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.ReaderTheme
import com.nextz.kanomtts.ui.theme.onSurface
import com.nextz.kanomtts.ui.theme.surface
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as RColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.Language

/** Pure mapping from Kanom display prefs to Readium EpubPreferences. (recon §2) */
@OptIn(ExperimentalReadiumApi::class)
object EpubPreferencesMapper {

    private const val BASE_FONT_SP = 16.0
    private const val BASE_MARGIN_DP = 16.0

    /**
     * Map [KanomPreferences] to [EpubPreferences] for submission to the navigator.
     *
     * Semantics:
     * - fontFamily = if (prefs.forceFont) familyOf(prefs.readingFont) else null
     *   where familyOf: SARABUN→FontFamily("Sarabun"), SERIF→FontFamily.SERIF, SYSTEM→null
     * - publisherStyles = prefs.usePublisherStyles && !prefs.stripColors
     * - backgroundColor/textColor: always from Kanom theme; true-black still DARK + black bg
     * - language = Language("th")
     */
    fun map(prefs: KanomPreferences): EpubPreferences {
        val readiumTheme = when (prefs.readerTheme) {
            ReaderTheme.LIGHT -> Theme.LIGHT
            ReaderTheme.SEPIA -> Theme.SEPIA
            ReaderTheme.DARK -> Theme.DARK
            ReaderTheme.TRUE_BLACK -> Theme.DARK
        }
        val bgArgb = if (prefs.readerTheme == ReaderTheme.TRUE_BLACK) AndroidColor.BLACK
        else prefs.readerTheme.surface().toArgb()
        val fgArgb = prefs.readerTheme.onSurface().toArgb()

        val family: FontFamily? = if (prefs.forceFont) {
            when (prefs.readingFont) {
                ReadingFont.SARABUN -> FontFamily("Sarabun")
                ReadingFont.SERIF -> FontFamily.SERIF
                ReadingFont.SYSTEM -> null
            }
        } else null

        return EpubPreferences(
            theme = readiumTheme,
            backgroundColor = RColor(bgArgb),
            textColor = RColor(fgArgb),
            fontFamily = family,
            fontSize = prefs.fontSizeSp / BASE_FONT_SP,
            lineHeight = prefs.lineHeight.toDouble(),
            pageMargins = prefs.marginDp / BASE_MARGIN_DP,
            publisherStyles = prefs.usePublisherStyles && !prefs.stripColors,
            language = Language("th"),
            // Force LTR for all v1 languages (Thai/English/horizontal JP+ZH).
            // Genuine RTL/vertical books are out of v1 scope.
            readingProgression = ReadingProgression.LTR,
        )
    }
}
