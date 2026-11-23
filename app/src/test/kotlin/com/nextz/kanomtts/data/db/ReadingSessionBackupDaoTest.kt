package com.nextz.kanomtts.data.db
import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
class ReadingSessionBackupDaoTest : RoomTestBase() {
    @Test fun `reading sessions can be exported and restored with stable ids`() = runTest {
        db.readingSessionDao().insert(ReadingSessionEntity(id = 42L, bookId = "book", startedAt = 100L, durationMs = 5000L))
        val snapshot = db.readingSessionDao().getAllForBackup()
        assertEquals(1, snapshot.size)
        db.readingSessionDao().deleteAll()
        db.readingSessionDao().insertAll(snapshot)
        val restored = db.readingSessionDao().getAllForBackup().single()
        assertEquals(42L, restored.id)
        assertEquals("book", restored.bookId)
        assertEquals(5000L, restored.durationMs)
    }
}
