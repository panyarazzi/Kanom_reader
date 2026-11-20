package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class AhoCorasickTest {
    @Test fun finds_longest_keyword_at_position() {
        val ac = AhoCorasick(listOf("ab", "abc"))
        assertEquals(3, ac.firstMatchAt("abcd", 0)) // longest = "abc"
    }

    @Test fun no_match_returns_minus_one() {
        val ac = AhoCorasick(listOf("xyz"))
        assertEquals(-1, ac.firstMatchAt("abc", 0))
    }

    @Test fun matches_thai_keyword() {
        val ac = AhoCorasick(listOf("กรุงเทพ"))
        assertEquals("กรุงเทพ".length, ac.firstMatchAt("ไป กรุงเทพ", 3))
    }
}
