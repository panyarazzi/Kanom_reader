package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uri = :uri OR (title <> '' AND fileSize > 0 AND title = :title AND fileSize = :fileSize))")
    suspend fun existsDuplicate(uri: String, title: String, fileSize: Long): Boolean

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
