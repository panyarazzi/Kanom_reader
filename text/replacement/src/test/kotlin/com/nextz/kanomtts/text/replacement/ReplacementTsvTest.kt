package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementTsvTest {
    @Test fun round_trips() {
        val rules = listOf(
            ReplacementRule("GPT", "จีพีที", isRegex = false, enabled = true, order = 1),
            ReplacementRule("""\d+""", "[เลข]", isRegex = true, enabled = false, order = 2),
        )
        assertEquals(rules, ReplacementTsv.import(ReplacementTsv.export(rules)))
    }

    @Test fun header_only_imports_empty() {
        assertEquals(emptyList<ReplacementRule>(), ReplacementTsv.import("pattern\treplacement\tisRegex\tenabled\torder"))
    }

    @Test fun malformed_lines_are_skipped() {
        val tsv = "pattern\treplacement\tisRegex\tenabled\torder\nGPT\tจีพีที\tfalse\ttrue\t1\nBAD_LINE"
        assertEquals(1, ReplacementTsv.import(tsv).size)
    }
}
