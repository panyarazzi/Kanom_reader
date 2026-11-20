package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookmarkEntity
import kotlinx.coroutines.flow.Flow

interface BookmarkStore {
    fun observe(bookId: String): Flow<List<BookmarkEntity>>
    suspend fun add(bookmark: BookmarkEntity): Long
    suspend fun remove(id: Long)
    suspend fun updateSnippet(id: Long, snippet: String)
}
