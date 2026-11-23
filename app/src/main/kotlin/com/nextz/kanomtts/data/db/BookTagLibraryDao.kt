package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookTagLibraryDao {
    @Query("SELECT * FROM book_tag_library ORDER BY tag COLLATE NOCASE")
    fun observeAll(): Flow<List<BookTagLibraryEntity>>

    @Query("SELECT * FROM book_tag_library ORDER BY tag COLLATE NOCASE")
    suspend fun getAllForBackup(): List<BookTagLibraryEntity>

    @Query("INSERT OR IGNORE INTO book_tag_library(tag) SELECT :tag WHERE NOT EXISTS (SELECT 1 FROM book_tag_library WHERE tag = :tag COLLATE NOCASE)")
    suspend fun insertIfMissing(tag: String)

    @Query("DELETE FROM book_tag_library WHERE tag = :tag COLLATE NOCASE")
    suspend fun delete(tag: String)

    @Query("DELETE FROM book_tag_library")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<BookTagLibraryEntity>)
}
