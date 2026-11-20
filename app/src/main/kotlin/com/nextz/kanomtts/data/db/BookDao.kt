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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllForBackup(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE title = :title AND fileSize = :fileSize LIMIT 1")
    suspend fun findByTitleAndSize(title: String, fileSize: Long): BookEntity?

    @Query("UPDATE books SET uri = :uri WHERE id = :id")
    suspend fun updateUri(id: String, uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uri = :uri OR (title <> '' AND fileSize > 0 AND title = :title AND fileSize = :fileSize))")
    suspend fun existsDuplicate(uri: String, title: String, fileSize: Long): Boolean

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM books")
    suspend fun deleteAll()
}
