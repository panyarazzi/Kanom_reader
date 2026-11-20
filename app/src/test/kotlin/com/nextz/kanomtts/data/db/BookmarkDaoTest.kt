package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkDaoTest : RoomTestBase() {
    @Test fun insert_and_observe_ordered_desc() = runTest {
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l1", snippet = "s1", createdAt = 1L))
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l2", snippet = "s2", createdAt = 2L))
        val list = db.bookmarkDao().observeForBook("b").first()
        assertEquals(2, list.size)
        assertEquals("s2", list.first().snippet)   // newest first
    }
    @Test fun delete_removes() = runTest {
        val id = db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l", snippet = "s", createdAt = 1L))
        db.bookmarkDao().delete(id)
        assertEquals(0, db.bookmarkDao().observeForBook("b").first().size)
    }
    @Test fun bookmarks_scoped_by_book() = runTest {
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b1", locator = "l", snippet = "s", createdAt = 1L))
        assertEquals(0, db.bookmarkDao().observeForBook("b2").first().size)
    }
    @Test fun updateSnippet_reflects_in_observe() = runTest {
        val id = db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l", snippet = "old", createdAt = 1L))
        db.bookmarkDao().updateSnippet(id, "new")
        val list = db.bookmarkDao().observeForBook("b").first()
        assertEquals(1, list.size)
        assertEquals("new", list.first().snippet)
    }
}
