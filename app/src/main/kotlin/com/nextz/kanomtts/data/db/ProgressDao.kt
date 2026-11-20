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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progress: List<ProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: ProgressEntity)

    @Query("SELECT * FROM progress")
    suspend fun getAllForBackup(): List<ProgressEntity>

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ProgressEntity?>

    @Query("UPDATE progress SET ttsLocator = :ttsLocator, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateTtsLocator(bookId: String, ttsLocator: String?, updatedAt: Long)

    @Query("UPDATE progress SET locator = :locator, percent = :percent, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long)

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)

    @Query("DELETE FROM progress")
    suspend fun deleteAll()
}
