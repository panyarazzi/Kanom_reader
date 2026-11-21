package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderHighlightDao {
    @Insert
    suspend fun insert(highlight: ReaderHighlightEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(highlights: List<ReaderHighlightEntity>)

    @Query("SELECT * FROM highlights")
    suspend fun getAllForBackup(): List<ReaderHighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<ReaderHighlightEntity>>

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM highlights WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)

    @Query("DELETE FROM highlights")
    suspend fun deleteAll()
}
