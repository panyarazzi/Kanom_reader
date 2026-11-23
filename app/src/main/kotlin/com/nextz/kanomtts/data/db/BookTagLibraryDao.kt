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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: BookTagLibraryEntity)

    @Query("DELETE FROM book_tag_library WHERE tag = :tag COLLATE NOCASE")
    suspend fun delete(tag: String)

    @Query("DELETE FROM book_tag_library")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<BookTagLibraryEntity>)
}
