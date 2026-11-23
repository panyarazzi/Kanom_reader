package com.nextz.kanomtts.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface ReadingSessionDao {
    @Insert suspend fun insert(session: ReadingSessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(sessions: List<ReadingSessionEntity>)
    @Query("SELECT * FROM reading_sessions") suspend fun getAllForBackup(): List<ReadingSessionEntity>
    @Query("DELETE FROM reading_sessions") suspend fun deleteAll()
    @Query("DELETE FROM reading_sessions WHERE bookId = :bookId") suspend fun deleteForBook(bookId: String)
    @Query("DELETE FROM reading_sessions WHERE startedAt < :cutoff") suspend fun deleteBefore(cutoff: Long)
    @Query("SELECT * FROM reading_sessions ORDER BY startedAt ASC") fun observeAll(): Flow<List<ReadingSessionEntity>>
    @Query("SELECT SUM(durationMs) FROM reading_sessions WHERE startedAt >= :fromEpochMs") fun observeTotalSince(fromEpochMs: Long): Flow<Long?>
}
