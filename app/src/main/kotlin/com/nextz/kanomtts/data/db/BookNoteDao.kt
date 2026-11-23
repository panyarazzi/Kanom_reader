package com.nextz.kanomtts.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface BookNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(note: BookNoteEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(notes: List<BookNoteEntity>)
    @Query("SELECT * FROM book_notes ORDER BY updatedAt DESC") fun observeAll(): Flow<List<BookNoteEntity>>
    @Query("SELECT * FROM book_notes") suspend fun getAllForBackup(): List<BookNoteEntity>
    @Query("SELECT * FROM book_notes WHERE bookId = :bookId") fun observeForBook(bookId: String): Flow<BookNoteEntity?>
    @Query("DELETE FROM book_notes WHERE bookId = :bookId") suspend fun deleteForBook(bookId: String)
    @Query("DELETE FROM book_notes") suspend fun deleteAll()
}
