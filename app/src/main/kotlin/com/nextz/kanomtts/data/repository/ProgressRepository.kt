package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ProgressDao
import com.nextz.kanomtts.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ProgressRepository @Inject constructor(private val dao: ProgressDao) : ProgressReader, ProgressWriter {
    override suspend fun save(progress: ProgressEntity) = dao.upsert(progress)
    override suspend fun get(bookId: String): ProgressEntity? = dao.get(bookId)
    override fun observe(bookId: String): Flow<ProgressEntity?> = dao.observe(bookId)

    override suspend fun saveTtsLocator(bookId: String, ttsLocator: String?, updatedAt: Long) {
        dao.insertIfAbsent(ProgressEntity(bookId, null, null, 0f, updatedAt))
        dao.updateTtsLocator(bookId, ttsLocator, updatedAt)
    }

    override suspend fun saveReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long) {
        dao.insertIfAbsent(ProgressEntity(bookId, null, null, 0f, updatedAt))
        dao.updateReadingLocator(bookId, locator, percent, updatedAt)
    }
}
