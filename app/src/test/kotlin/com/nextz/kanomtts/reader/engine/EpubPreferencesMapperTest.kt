package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalReadiumApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EpubPreferencesMapperTest {
    // ── Existing tests updated to new single-arg signature ─────────────────────

    @Test fun `true black maps to DARK theme with black background`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readerTheme = ReaderTheme.TRUE_BLACK))
        assertEquals(Theme.DARK, p.theme)
        assertEquals(android.graphics.Color.BLACK, p.backgroundColor!!.int)
    }

    @Test fun `sarabun maps to named family`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readingFont = ReadingFont.SARABUN))
        assertEquals(FontFamily("Sarabun"), p.fontFamily)
    }

    @Test fun `system font with forceFont off leaves family null`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readingFont = ReadingFont.SYSTEM, forceFont = false))
        assertNull(p.fontFamily)
    }

    @Test fun `font size maps to ratio over 16`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(fontSizeSp = 24))
        assertEquals(24.0 / 16.0, p.fontSize!!, 1e-9)
    }

    @Test fun `line height below one is passed through to Readium`() {
        assertEquals(0.7, EpubPreferencesMapper.map(KanomPreferences(lineHeight = 0.7f)).lineHeight!!, 1e-6)
    }

    @Test fun `bold toggle requests bold and leaves publisher weight untouched when off`() {
        assertEquals(1.75, EpubPreferencesMapper.map(KanomPreferences(boldText = true)).fontWeight!!, 0.0)
        assertNull(EpubPreferencesMapper.map(KanomPreferences(boldText = false)).fontWeight)
    }

    @Test fun `usePublisherStyles false by default so typography applies`() {
        assertEquals(false, EpubPreferencesMapper.map(KanomPreferences()).publisherStyles)
    }

    // ── Truth-table tests for Task 6 escape hatches ────────────────────────────

    @Test fun `usePublisherStyles true and stripColors false yields publisherStyles true`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(usePublisherStyles = true, stripColors = false))
        assertEquals(true, p.publisherStyles)
    }

    @Test fun `stripColors true overrides usePublisherStyles yielding publisherStyles false`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(usePublisherStyles = true, stripColors = true))
        assertEquals(false, p.publisherStyles)
    }

    @Test fun `forceFont false yields null fontFamily even for SARABUN`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readingFont = ReadingFont.SARABUN, forceFont = false))
        assertNull(p.fontFamily)
    }

    @Test fun `forceFont true with SARABUN yields named family`() {
        val p = EpubPreferencesMapper.map(KanomPreferences(readingFont = ReadingFont.SARABUN, forceFont = true))
        assertEquals(FontFamily("Sarabun"), p.fontFamily)
    }

    @Test fun `readingProgression is always LTR`() {
        // Forces LTR for Thai/English/horizontal JP+ZH; genuine RTL/vertical out of v1 scope.
        assertEquals(ReadingProgression.LTR, EpubPreferencesMapper.map(KanomPreferences()).readingProgression)
    }
}
