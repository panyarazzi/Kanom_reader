package com.nextz.kanomtts.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface BookTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(tag: BookTagEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(tags: List<BookTagEntity>)
    @Query("SELECT * FROM book_tags ORDER BY tag COLLATE NOCASE, bookId") fun observeAll(): Flow<List<BookTagEntity>>
    @Query("SELECT * FROM book_tags") suspend fun getAllForBackup(): List<BookTagEntity>
    @Query("SELECT * FROM book_tags WHERE bookId = :bookId ORDER BY tag COLLATE NOCASE") fun observeForBook(bookId: String): Flow<List<BookTagEntity>>
    @Query("DELETE FROM book_tags WHERE bookId = :bookId") suspend fun deleteForBook(bookId: String)
    @Query("DELETE FROM book_tags") suspend fun deleteAll()
}
