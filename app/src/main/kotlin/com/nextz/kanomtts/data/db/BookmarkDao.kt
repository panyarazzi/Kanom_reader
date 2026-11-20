package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert suspend fun insert(bookmark: BookmarkEntity): Long
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("UPDATE bookmarks SET snippet = :snippet WHERE id = :id")
    suspend fun updateSnippet(id: Long, snippet: String)
    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
