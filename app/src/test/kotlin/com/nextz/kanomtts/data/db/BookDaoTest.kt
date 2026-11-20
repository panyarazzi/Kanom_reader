package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDaoTest : RoomTestBase() {
    private fun book(id: String, uri: String = "uri://$id", title: String = "T$id", size: Long = 100) =
        BookEntity(id, uri, title, null, "th", null, size, 1L, null)

    @Test fun upsert_then_query_round_trips() = runTest {
        db.bookDao().upsert(book("a"))
        assertEquals("Ta", db.bookDao().getById("a")?.title)
    }

    @Test fun observeAll_emits_inserted_books() = runTest {
        db.bookDao().upsert(book("a"))
        db.bookDao().upsert(book("b"))
        assertEquals(2, db.bookDao().observeAll().first().size)
    }

    @Test fun duplicate_detection_by_uri_or_title_and_size() = runTest {
        db.bookDao().upsert(book("a", uri = "uri://x", title = "Same", size = 50))
        assertTrue(db.bookDao().existsDuplicate("uri://x", "other", 999))     // uri match
        assertTrue(db.bookDao().existsDuplicate("uri://other", "Same", 50))   // title+size match
        assertFalse(db.bookDao().existsDuplicate("uri://other", "other", 999))
    }

    @Test fun delete_removes_book() = runTest {
        db.bookDao().upsert(book("a"))
        db.bookDao().delete("a")
        assertEquals(null, db.bookDao().getById("a"))
    }

    @Test fun blank_title_or_zero_size_does_not_false_positive() = runTest {
        db.bookDao().upsert(book("a", uri = "uri://a", title = "", size = 0))
        // a different book with blank title + zero size must NOT be flagged via the heuristic
        assertFalse(db.bookDao().existsDuplicate("uri://b", "", 0))
        // but a same-uri re-add is still a duplicate
        assertTrue(db.bookDao().existsDuplicate("uri://a", "whatever", 999))
    }
}
