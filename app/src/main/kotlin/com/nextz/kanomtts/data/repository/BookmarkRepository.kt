package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookmarkDao
import com.nextz.kanomtts.data.db.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookmarkRepository @Inject constructor(private val dao: BookmarkDao) : BookmarkStore {
    override fun observe(bookId: String): Flow<List<BookmarkEntity>> = dao.observeForBook(bookId)
    override suspend fun add(bookmark: BookmarkEntity): Long = dao.insert(bookmark)
    override suspend fun remove(id: Long) = dao.delete(id)
    override suspend fun updateSnippet(id: Long, snippet: String) = dao.updateSnippet(id, snippet)
}
