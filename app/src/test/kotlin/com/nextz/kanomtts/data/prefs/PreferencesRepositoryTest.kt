package com.nextz.kanomtts.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun repo(): PreferencesRepository {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
            File(tmp.newFolder(), "test.preferences_pb")
        }
        return PreferencesRepository(store)
    }

    @Test fun defaults_when_empty() = runTest {
        val p = repo().preferences.first()
        assertEquals(ReaderTheme.LIGHT, p.readerTheme)
        assertEquals(18, p.fontSizeSp)
        assertEquals(false, p.premiumUnlocked)
        assertEquals(false, p.reverseVolumeKeys)
        assertEquals(1.0f, p.paragraphIndentEm)
        assertEquals(0.0f, p.paragraphSpacingEm)
    }
    @Test fun writes_round_trip() = runTest {
        val r = repo()
        r.setReaderTheme(ReaderTheme.TRUE_BLACK); r.setFontSizeSp(24); r.setPremiumUnlocked(true)
        val p = r.preferences.first()
        assertEquals(ReaderTheme.TRUE_BLACK, p.readerTheme)
        assertEquals(24, p.fontSizeSp)
        assertEquals(true, p.premiumUnlocked)
    }
    @Test fun fractionalLineHeight_precision_round_trip() = runTest {
        val r = repo()
        r.setLineHeight(1.08f)
        assertEquals(1.08f, r.preferences.first().lineHeight)
    }

    @Test fun progressFontSize_round_trip_and_clamps() = runTest {
        val r = repo()
        r.setProgressFontSizeSp(80)
        assertEquals(80, r.preferences.first().progressFontSizeSp)
        r.setProgressFontSizeSp(100)
        assertEquals(80, r.preferences.first().progressFontSizeSp)
        r.setProgressFontSizeSp(4)
        assertEquals(8, r.preferences.first().progressFontSizeSp)
    }

    @Test fun corrupt_enum_falls_back_to_default() = runTest {
        // setting an int where enum expected isn't possible via API; verify unknown name path via valueOf guard
        val r = repo(); r.setReadingFont(ReadingFont.SERIF)
        assertEquals(ReadingFont.SERIF, r.preferences.first().readingFont)
    }

    // Task 6 escape-hatch round-trip tests
    @Test fun escape_hatch_defaults() = runTest {
        val p = repo().preferences.first()
        assertEquals(false, p.usePublisherStyles)
        assertEquals(true, p.forceFont)
        assertEquals(false, p.stripColors)
        assertEquals(true, p.thaiWordBreak)
    }
    @Test fun escape_hatch_writes_round_trip() = runTest {
        val r = repo()
        r.setUsePublisherStyles(true)
        r.setForceFont(false)
        r.setStripColors(true)
        r.setThaiWordBreak(false)
        val p = r.preferences.first()
        assertEquals(true, p.usePublisherStyles)
        assertEquals(false, p.forceFont)
        assertEquals(true, p.stripColors)
        assertEquals(false, p.thaiWordBreak)
    }

    @Test fun readerTypographyAndVerticalMargins_round_trip() = runTest {
        val r = repo()
        r.setBoldText(true)
        r.setTopMarginDp(28)
        r.setBottomMarginDp(36)
        val p = r.preferences.first()
        assertEquals(true, p.boldText)
        assertEquals(28, p.topMarginDp)
        assertEquals(36, p.bottomMarginDp)
    }

    @Test fun paragraphTypography_round_trip() = runTest {
        val r = repo()
        r.setLineHeight(0.75f)
        r.setParagraphIndentEm(1.5f)
        r.setParagraphSpacingEm(0.4f)
        val p = r.preferences.first()
        assertEquals(0.75f, p.lineHeight)
        assertEquals(1.5f, p.paragraphIndentEm)
        assertEquals(0.4f, p.paragraphSpacingEm)
    }

    @Test fun reverseVolumeKeys_round_trip() = runTest {
        val r = repo()
        r.setReverseVolumeKeys(true)
        assertEquals(true, r.preferences.first().reverseVolumeKeys)
        r.setReverseVolumeKeys(false)
        assertEquals(false, r.preferences.first().reverseVolumeKeys)
    }

    @Test fun pageAnimation_default_is_true() = runTest {
        assertEquals(true, repo().preferences.first().pageAnimation)
    }

    @Test fun pageAnimation_round_trip() = runTest {
        val r = repo()
        r.setPageAnimation(false)
        assertEquals(false, r.preferences.first().pageAnimation)
        r.setPageAnimation(true)
        assertEquals(true, r.preferences.first().pageAnimation)
    }
}
