package com.nextz.kanomtts.data.db
import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
class ReaderHighlightDaoTest : RoomTestBase() {
    @Test fun insertAndObserveNewestFirst() = runTest {
        db.highlightDao().insert(ReaderHighlightEntity(bookId = "b", locator = "l1", selectedText = "one", createdAt = 1L))
        db.highlightDao().insert(ReaderHighlightEntity(bookId = "b", locator = "l2", selectedText = "two", createdAt = 2L))
        val rows = db.highlightDao().observeForBook("b").first()
        assertEquals(listOf("two", "one"), rows.map { it.selectedText })
    }
    @Test fun highlightsAreScopedByBookAndCanBeDeleted() = runTest {
        val id = db.highlightDao().insert(ReaderHighlightEntity(bookId = "b1", locator = "l", selectedText = "text", createdAt = 1L))
        assertEquals(0, db.highlightDao().observeForBook("b2").first().size)
        db.highlightDao().delete(id)
        assertEquals(0, db.highlightDao().observeForBook("b1").first().size)
    }
    @Test fun backupQueryReturnsFullAnnotationData() = runTest {
        val row = ReaderHighlightEntity(bookId = "b", locator = """{"href":"chapter.xhtml","text":{"highlight":"selected"}}""",
            selectedText = "selected", beforeText = "before", afterText = "after", createdAt = 12L)
        db.highlightDao().insert(row)
        val restored = db.highlightDao().getAllForBackup().single()
        assertEquals(row.copy(id = restored.id), restored)
    }
}
