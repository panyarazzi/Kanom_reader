package com.nextz.kanomtts.data.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(book: BookEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(books: List<BookEntity>)
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC") fun observeAll(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books") suspend fun getAllForBackup(): List<BookEntity>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun getById(id: String): BookEntity?
    @Query("UPDATE books SET uri = :uri, sha256 = :sha256, sourceFolder = :sourceFolder, sourceFileName = :sourceFileName WHERE id = :id")
    suspend fun updateSource(id: String, uri: String, sha256: String?, sourceFolder: String?, sourceFileName: String?)
    @Query("UPDATE books SET title = :title, author = :author WHERE id = :id")
    suspend fun updateMetadata(id: String, title: String, author: String?)
    @Query("UPDATE books SET coverPath = :coverPath WHERE id = :id")
    suspend fun updateCoverPath(id: String, coverPath: String?)
    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uri = :uri)") suspend fun existsUri(uri: String): Boolean
    @Query("DELETE FROM books WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM books") suspend fun deleteAll()
}
