package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementEngineTest {
    @Test fun literal_replacement_applied() {
        val e = ReplacementEngine(listOf(ReplacementRule("GPT", "จีพีที")))
        assertEquals("อ่าน จีพีที ดู", e.apply("อ่าน GPT ดู"))
    }

    @Test fun longest_literal_wins_at_a_position() {
        val e = ReplacementEngine(listOf(
            ReplacementRule("AI", "เอไอ", order = 1),
            ReplacementRule("AIX", "เอไอเอกซ์", order = 2),
        ))
        assertEquals("เอไอเอกซ์", e.apply("AIX"))
    }

    @Test fun disabled_rule_is_skipped() {
        val e = ReplacementEngine(listOf(ReplacementRule("X", "Y", enabled = false)))
        assertEquals("X", e.apply("X"))
    }

    @Test fun regex_rule_applied_after_literals() {
        val e = ReplacementEngine(listOf(
            ReplacementRule("""\d+""", "[เลข]", isRegex = true)
        ))
        assertEquals("หน้า [เลข]", e.apply("หน้า 123"))
    }

    @Test fun no_rules_is_identity() {
        assertEquals("hello", ReplacementEngine(emptyList()).apply("hello"))
    }

    @Test fun earliest_order_wins_for_duplicate_pattern() {
        val e = ReplacementEngine(listOf(
            ReplacementRule("X", "first", order = 1),
            ReplacementRule("X", "second", order = 2),
        ))
        assertEquals("first", e.apply("X"))
    }

    @Test fun empty_pattern_rule_is_ignored_and_does_not_loop() {
        val e = ReplacementEngine(listOf(ReplacementRule("", "X")))
        assertEquals("abc", e.apply("abc"))
    }
}
