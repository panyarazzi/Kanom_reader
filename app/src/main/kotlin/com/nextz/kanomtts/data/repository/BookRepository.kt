package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookRepository @Inject constructor(private val dao: BookDao) : BookWriter, BookReader {
    override fun observeLibrary(): Flow<List<BookEntity>> = dao.observeAll()
    override suspend fun get(id: String): BookEntity? = dao.getById(id)
    override suspend fun add(book: BookEntity) = dao.upsert(book)
    override suspend fun isDuplicate(uri: String, title: String, fileSize: Long): Boolean =
        dao.existsDuplicate(uri, title, fileSize)
    override suspend fun relinkSource(uri: String, title: String, fileSize: Long): String? {
        val existing = dao.findByTitleAndSize(title, fileSize) ?: return null
        if (existing.uri == uri) return null
        dao.updateUri(existing.id, uri)
        return existing.id
    }

    suspend fun remove(id: String) = dao.delete(id)
}
