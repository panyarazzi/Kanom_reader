package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementRuleDaoTest : RoomTestBase() {
    private fun rule(scope: String, bookId: String?, pattern: String, pos: Int) =
        ReplacementRuleEntity(scope = scope, bookId = bookId, pattern = pattern,
            replacement = "x", isRegex = false, enabled = true, position = pos)

    @Test fun effective_includes_global_and_book_ordered() = runTest {
        db.replacementRuleDao().insert(rule("global", null, "g", 0))
        db.replacementRuleDao().insert(rule("book", "b1", "b1rule", 1))
        db.replacementRuleDao().insert(rule("book", "b2", "b2rule", 2))
        val eff = db.replacementRuleDao().observeEffective("b1").first()
        assertEquals(listOf("g", "b1rule"), eff.map { it.pattern })   // excludes b2's rule
    }
    @Test fun maxPosition_is_minus1_when_empty_then_tracks_max() = runTest {
        assertEquals(-1, db.replacementRuleDao().maxPosition())
        db.replacementRuleDao().insert(rule("global", null, "g", 7))
        assertEquals(7, db.replacementRuleDao().maxPosition())
    }
}
