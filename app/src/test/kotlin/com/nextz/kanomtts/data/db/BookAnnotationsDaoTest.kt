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
    @Test fun `removing a reusable tag never removes book assignments and duplicates ignore case`() = runTest {
        db.bookDao().upsert(book())
        db.bookTagDao().upsert(BookTagEntity("b1", "แนะนำ"))
        val library = db.bookTagLibraryDao()
        library.insertIfMissing("Review")
        library.insertIfMissing("review")

        assertEquals(1, library.getAllForBackup().size)
        library.delete("Review")

        assertEquals(emptyList<BookTagLibraryEntity>(), library.getAllForBackup())
        assertEquals(listOf("แนะนำ"), db.bookTagDao().observeForBook("b1").first().map { it.tag })
    }
    @Test fun `manual reading status persists separately from reading locator progress`() = runTest {
        db.bookDao().upsert(book())
        db.progressDao().upsert(ProgressEntity("b1", "locator-snapshot", null, 0.97f, 20L))

        db.bookDao().updateReadingStatus("b1", "TO_READ")

        assertEquals("TO_READ", db.bookDao().getById("b1")?.readingStatus)
        assertEquals(0.97f, db.progressDao().get("b1")?.percent ?: 0f)
        assertEquals("locator-snapshot", db.progressDao().get("b1")?.locator)
    }

    @Test fun `editing metadata leaves original EPUB URI and source filename unchanged`() = runTest {
        db.bookDao().upsert(book().copy(sourceFileName = "original.epub"))
        db.bookDao().updateMetadata("b1", "Edited title", "Edited author", "Saga", 2.0)

        val updated = db.bookDao().getById("b1")!!
        assertEquals("Edited title", updated.title)
        assertEquals("Edited author", updated.author)
        assertEquals("Saga", updated.seriesName)
        assertEquals(2.0, updated.seriesPosition!!, 0.0)
        assertEquals("content://b1", updated.uri)
        assertEquals("original.epub", updated.sourceFileName)
    }


}
