package com.nextz.kanomtts.feature.replacement

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementReorderTest {
    private fun e(pat: String, pos: Int) = ReplacementRuleEntity(
        scope = "global", bookId = null, pattern = pat, replacement = "x",
        isRegex = false, enabled = true, position = pos,
    )
    @Test fun `move down reindexes positions contiguously`() {
        val list = listOf(e("a", 0), e("b", 1), e("c", 2))
        val out = ReplacementReorder.reassignPositions(list, from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), out.map { it.pattern })
        assertEquals(listOf(0, 1, 2), out.map { it.position })
    }
    @Test fun `move up reindexes positions`() {
        val list = listOf(e("a", 0), e("b", 1), e("c", 2))
        val out = ReplacementReorder.reassignPositions(list, from = 2, to = 0)
        assertEquals(listOf("c", "a", "b"), out.map { it.pattern })
        assertEquals(listOf(0, 1, 2), out.map { it.position })
    }
    @Test fun `no-op or out-of-range still normalizes positions`() {
        val list = listOf(e("a", 5), e("b", 9))
        val out = ReplacementReorder.reassignPositions(list, from = 0, to = 0)
        assertEquals(listOf("a", "b"), out.map { it.pattern })
        assertEquals(listOf(0, 1), out.map { it.position })
    }
}
