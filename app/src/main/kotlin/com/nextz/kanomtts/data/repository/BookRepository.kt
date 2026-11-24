package com.nextz.kanomtts.data.repository
import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
class BookRepository @Inject constructor(private val dao: BookDao) : BookWriter, BookReader {
    override fun observeLibrary(): Flow<List<BookEntity>> = dao.observeAll()
    override suspend fun get(id: String): BookEntity? = dao.getById(id)
    override suspend fun add(book: BookEntity) = dao.upsert(book)
    override suspend fun isDuplicate(uri: String): Boolean = dao.existsUri(uri)
    override suspend fun updateImportLocation(uri: String, sourceFolder: String, sourceFileName: String?): Boolean =
        dao.updateImportLocation(uri, sourceFolder, sourceFileName) > 0
    override suspend fun updateSource(bookId: String, uri: String, sha256: String?, sourceFolder: String?, sourceFileName: String?): Boolean {
        if (dao.getById(bookId) == null) return false
        dao.updateSource(bookId, uri, sha256, sourceFolder, sourceFileName)
        return true
    }
    suspend fun remove(id: String) = dao.delete(id)
}
