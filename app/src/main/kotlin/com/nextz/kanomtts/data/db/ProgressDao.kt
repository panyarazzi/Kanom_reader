package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    /**
     * Insert a baseline row only if one does not already exist.
     * Used before partial-column UPDATE queries to ensure the row exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ProgressEntity?>

    /**
     * Update only the TTS locator column — does NOT touch the reading locator.
     * Only affects an existing row; call [insertIfAbsent] first if the row may be missing.
     */
    @Query("UPDATE progress SET ttsLocator = :ttsLocator, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateTtsLocator(bookId: String, ttsLocator: String?, updatedAt: Long)

    /**
     * Update only the reading locator + percent columns — does NOT touch ttsLocator.
     * Only affects an existing row; call [insertIfAbsent] first if the row may be missing.
     */
    @Query("UPDATE progress SET locator = :locator, percent = :percent, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
