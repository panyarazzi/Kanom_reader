package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {
    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt ASC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("DELETE FROM reading_sessions WHERE startedAt < :cutoff")
    suspend fun deleteBefore(cutoff: Long)
}
