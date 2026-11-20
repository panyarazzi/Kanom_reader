package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementRuleVisualDaoTest : RoomTestBase() {
    private fun rule(scope: String, bookId: String?, pat: String, show: Boolean, pos: Int) =
        ReplacementRuleEntity(scope = scope, bookId = bookId, pattern = pat, replacement = "x",
            isRegex = false, enabled = true, position = pos, showInReader = show)

    @Test fun `observeEffectiveVisual returns only showInReader effective rules ordered`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(rule("global", null, "g-show", true, 0))
        dao.insert(rule("global", null, "g-hide", false, 1))
        dao.insert(rule("book", "b1", "b-show", true, 2))
        dao.insert(rule("book", "b2", "other", true, 3))
        assertEquals(listOf("g-show", "b-show"), dao.observeEffectiveVisual("b1").first().map { it.pattern })
    }
}
