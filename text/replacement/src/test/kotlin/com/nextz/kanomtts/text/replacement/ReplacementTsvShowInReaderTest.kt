package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementTsvShowInReaderTest {
    @Test fun `export then import round-trips showInReader`() {
        val rules = listOf(
            ReplacementRule("a", "b", isRegex = false, enabled = true, order = 0, showInReader = true),
            ReplacementRule("c", "d", isRegex = false, enabled = true, order = 1, showInReader = false),
        )
        assertEquals(rules, ReplacementTsv.import(ReplacementTsv.export(rules)))
    }
    @Test fun `header has the showInReader column`() {
        assertEquals("pattern\treplacement\tisRegex\tenabled\torder\tshowInReader", ReplacementTsv.export(emptyList()))
    }
    @Test fun `old 5-column rows import with showInReader false`() {
        val old = "pattern\treplacement\tisRegex\tenabled\torder\nก\tข\tfalse\ttrue\t0"
        val imported = ReplacementTsv.import(old)
        assertEquals(1, imported.size)
        assertEquals(false, imported.first().showInReader)
    }
}
