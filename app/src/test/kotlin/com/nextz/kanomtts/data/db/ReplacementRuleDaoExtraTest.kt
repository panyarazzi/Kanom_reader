package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementRuleDaoExtraTest : RoomTestBase() {
    private fun rule(scope: String, bookId: String?, pattern: String, pos: Int) =
        ReplacementRuleEntity(scope = scope, bookId = bookId, pattern = pattern,
            replacement = "x", isRegex = false, enabled = true, position = pos)

    @Test fun `observeByBook returns only that book's rules, ordered`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(rule("global", null, "g", 0))
        dao.insert(rule("book", "b1", "second", 2))
        dao.insert(rule("book", "b1", "first", 1))
        dao.insert(rule("book", "b2", "other", 0))
        assertEquals(listOf("first", "second"), dao.observeByBook("b1").first().map { it.pattern })
    }
    @Test fun `globalCount counts only global rules`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(rule("global", null, "g1", 0))
        dao.insert(rule("global", null, "g2", 1))
        dao.insert(rule("book", "b1", "b", 0))
        assertEquals(2, dao.globalCount().first())
    }
    @Test fun `updateAll persists new positions`() = runTest {
        val dao = db.replacementRuleDao()
        val id1 = dao.insert(rule("global", null, "a", 0))
        val id2 = dao.insert(rule("global", null, "b", 1))
        dao.updateAll(listOf(
            rule("global", null, "a", 1).copy(id = id1),
            rule("global", null, "b", 0).copy(id = id2),
        ))
        assertEquals(listOf("b", "a"), dao.observeGlobal().first().map { it.pattern }) // reordered by position
    }
}
