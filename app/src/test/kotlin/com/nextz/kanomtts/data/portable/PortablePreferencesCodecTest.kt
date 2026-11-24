package com.nextz.kanomtts.data.portable

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PortablePreferencesCodecTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun backupEncodesCustomTypographyAndTapNavigationButExcludesPremiumEntitlement() = runTest {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
            File(tmp.newFolder(), "portable.preferences_pb")
        }
        store.edit {
            it[stringPreferencesKey("book_custom_typography")] = """{"lineHeight":1.35,"indent":1.2}"""
            it[stringPreferencesKey("library_presentation_mode")] = "CLASSIC"
            it[booleanPreferencesKey("tap_anywhere_to_next")] = true
            it[floatPreferencesKey("line_height")] = 1.35f
            it[floatPreferencesKey("paragraph_indent_em")] = 1.4f
            it[floatPreferencesKey("paragraph_spacing_em")] = 0.6f
            it[booleanPreferencesKey("premium_unlocked")] = true
        }

        val encoded = encodePortablePreferences(store.data.first())
        val byName = (0 until encoded.length()).associate { index ->
            val entry = encoded.getJSONObject(index)
            entry.getString("name") to entry
        }

        assertEquals("string", byName.getValue("book_custom_typography").getString("type"))
        assertEquals("""{"lineHeight":1.35,"indent":1.2}""", byName.getValue("book_custom_typography").getString("value"))
        assertEquals(true, byName.getValue("tap_anywhere_to_next").getBoolean("value"))
        assertEquals("CLASSIC", byName.getValue("library_presentation_mode").getString("value"))
        assertEquals(1.35, byName.getValue("line_height").getDouble("value"), 0.0001)
        assertEquals(1.4, byName.getValue("paragraph_indent_em").getDouble("value"), 0.0001)
        assertEquals(0.6, byName.getValue("paragraph_spacing_em").getDouble("value"), 0.0001)
        assertFalse(byName.containsKey("premium_unlocked"))
    }
}
