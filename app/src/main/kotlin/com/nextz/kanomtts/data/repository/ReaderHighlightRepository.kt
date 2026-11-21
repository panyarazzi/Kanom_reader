package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReaderHighlightDao
import com.nextz.kanomtts.data.db.ReaderHighlightEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ReaderHighlightRepository @Inject constructor(
    private val dao: ReaderHighlightDao,
) : ReaderHighlightStore {
    override fun observe(bookId: String): Flow<List<ReaderHighlightEntity>> = dao.observeForBook(bookId)
    override suspend fun add(highlight: ReaderHighlightEntity): Long = dao.insert(highlight)
    override suspend fun remove(id: Long) = dao.delete(id)
}
