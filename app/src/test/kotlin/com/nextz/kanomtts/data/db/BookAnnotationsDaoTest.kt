package com.nextz.kanomtts.data.db
import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
class BookAnnotationsDaoTest : RoomTestBase() {
    private fun book() = BookEntity(
        id = "b1", uri = "content://b1", title = "Book", author = null, lang = "th",
        coverPath = null, fileSize = 100L, addedAt = 1L, lastOpenedAt = null,
    )
    @Test fun `tags and notes persist per book and cascade on book deletion`() = runTest {
        db.bookDao().upsert(book())
        db.bookTagDao().upsert(BookTagEntity("b1", "recommended"))
        db.bookNoteDao().upsert(BookNoteEntity("b1", "แปลดี อ่านสนุก", 10L))
        assertEquals(listOf("recommended"), db.bookTagDao().observeForBook("b1").first().map { it.tag })
        assertEquals("แปลดี อ่านสนุก", db.bookNoteDao().observeForBook("b1").first()?.note)
        db.bookDao().delete("b1")
        assertEquals(emptyList<BookTagEntity>(), db.bookTagDao().getAllForBackup())
        assertEquals(emptyList<BookNoteEntity>(), db.bookNoteDao().getAllForBackup())
    }
}
