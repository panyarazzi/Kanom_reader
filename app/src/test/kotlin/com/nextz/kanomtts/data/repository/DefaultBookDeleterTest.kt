package com.nextz.kanomtts.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.db.ReadingSessionEntity
import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class DefaultBookDeleterTest : RoomTestBase() {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private fun deleter() = DefaultBookDeleter(
        db.bookDao(), db.progressDao(), db.bookmarkDao(), db.replacementRuleDao(), db.readingSessionDao(), ctx,
    )

    private suspend fun book(id: String, uri: String, cover: String?) = db.bookDao().upsert(
        BookEntity(id = id, uri = uri, title = id, author = null, lang = null,
            coverPath = cover, fileSize = 1L, addedAt = 0L, lastOpenedAt = null),
    )

    @Test fun `delete removes book rows cover file releases SAF and keeps global rule and other book`() = runTest {
        val coverFile = File.createTempFile("cover", ".png", ctx.cacheDir).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val uri = "content://test/b1.epub"
        ctx.contentResolver.takePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)

        book("b1", uri, coverFile.absolutePath)
        book("b2", "content://test/b2.epub", null)
        db.progressDao().upsert(ProgressEntity("b1", null, null, 0.5f, 0L))
        db.readingSessionDao().insert(ReadingSessionEntity(bookId = "b1", startedAt = 1L, durationMs = 1000L))
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b1", locator = "{}", snippet = "s", createdAt = 0L))
        db.replacementRuleDao().insert(ReplacementRuleEntity(scope = "book", bookId = "b1", pattern = "a", replacement = "b", isRegex = false, enabled = true, position = 0))
        db.replacementRuleDao().insert(ReplacementRuleEntity(scope = "global", bookId = null, pattern = "g", replacement = "h", isRegex = false, enabled = true, position = 1))

        deleter().delete("b1")

        assertNull(db.bookDao().getById("b1"))
        assertNotNull(db.bookDao().getById("b2"))
        assertNull(db.progressDao().get("b1"))
        assertEquals(emptyList<ReadingSessionEntity>(), db.readingSessionDao().getAllForBackup())
        assertEquals(0, db.bookmarkDao().observeForBook("b1").first().size)
        assertEquals(emptyList<String>(), db.replacementRuleDao().observeByBook("b1").first().map { it.pattern })
        assertEquals(listOf("g"), db.replacementRuleDao().observeGlobal().first().map { it.pattern })
        assertFalse(coverFile.exists())
        assertEquals(0, ctx.contentResolver.persistedUriPermissions.size)
    }

    @Test fun `delete is a no-op when the book is already gone`() = runTest {
        deleter().delete("missing") // must not throw
        assertEquals(0, db.bookmarkDao().observeForBook("missing").first().size)
    }
}
