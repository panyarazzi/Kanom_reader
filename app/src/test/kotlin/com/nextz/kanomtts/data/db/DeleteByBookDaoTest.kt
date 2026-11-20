package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteByBookDaoTest : RoomTestBase() {
    @Test fun `progress deleteByBook removes only that book`() = runTest {
        val dao = db.progressDao()
        dao.upsert(ProgressEntity(bookId = "b1", locator = null, ttsLocator = null, percent = 0.5f, updatedAt = 0L))
        dao.upsert(ProgressEntity(bookId = "b2", locator = null, ttsLocator = null, percent = 0.2f, updatedAt = 0L))
        dao.deleteByBook("b1")
        assertNull(dao.get("b1"))
        assertNotNull(dao.get("b2"))
    }

    @Test fun `bookmark deleteByBook removes only that book`() = runTest {
        val dao = db.bookmarkDao()
        dao.insert(BookmarkEntity(bookId = "b1", locator = "{}", snippet = "s1", createdAt = 0L))
        dao.insert(BookmarkEntity(bookId = "b2", locator = "{}", snippet = "s2", createdAt = 0L))
        dao.deleteByBook("b1")
        assertEquals(0, dao.observeForBook("b1").first().size)
        assertEquals(1, dao.observeForBook("b2").first().size)
    }

    @Test fun `replacement deleteByBook removes that book's rules but keeps global`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(ReplacementRuleEntity(scope = "book", bookId = "b1", pattern = "a", replacement = "b", isRegex = false, enabled = true, position = 0))
        dao.insert(ReplacementRuleEntity(scope = "book", bookId = "b2", pattern = "c", replacement = "d", isRegex = false, enabled = true, position = 1))
        dao.insert(ReplacementRuleEntity(scope = "global", bookId = null, pattern = "g", replacement = "h", isRegex = false, enabled = true, position = 2))
        dao.deleteByBook("b1")
        assertEquals(emptyList<String>(), dao.observeByBook("b1").first().map { it.pattern })
        assertEquals(listOf("c"), dao.observeByBook("b2").first().map { it.pattern })
        assertEquals(listOf("g"), dao.observeGlobal().first().map { it.pattern })
    }
}
