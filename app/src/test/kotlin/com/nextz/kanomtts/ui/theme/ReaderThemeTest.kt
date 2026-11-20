package com.nextz.kanomtts.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderThemeTest {
    @Test fun every_reader_theme_maps_to_surface_and_ink() {
        ReaderTheme.entries.forEach { t ->
            assertNotEquals("surface must differ from ink for $t", t.surface(), t.onSurface())
        }
    }
    @Test fun true_black_surface_is_pure_black() {
        assertEquals(TrueBlack, ReaderTheme.TRUE_BLACK.surface())
    }
    @Test fun light_reader_surface_is_coconut_cream() {
        assertEquals(CoconutCream, ReaderTheme.LIGHT.surface())
    }
}
