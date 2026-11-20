package com.nextz.kanomtts.playback.util

import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.repository.ProgressReader
import com.nextz.kanomtts.data.repository.ProgressWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake implementing both [ProgressReader] and [ProgressWriter] for playback unit tests.
 */
class FakeProgressRepository : ProgressReader, ProgressWriter {
    private val store = MutableStateFlow<Map<String, ProgressEntity>>(emptyMap())

    override suspend fun get(bookId: String): ProgressEntity? = store.value[bookId]

    override fun observe(bookId: String): Flow<ProgressEntity?> = store.map { it[bookId] }

    override suspend fun save(progress: ProgressEntity) {
        store.value = store.value + (progress.bookId to progress)
    }

    override suspend fun saveTtsLocator(bookId: String, ttsLocator: String?, updatedAt: Long) {
        val existing = store.value[bookId]
            ?: ProgressEntity(bookId, null, null, 0f, updatedAt)
        store.value = store.value + (bookId to existing.copy(ttsLocator = ttsLocator, updatedAt = updatedAt))
    }

    override suspend fun saveReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long) {
        val existing = store.value[bookId]
            ?: ProgressEntity(bookId, null, null, 0f, updatedAt)
        store.value = store.value + (bookId to existing.copy(locator = locator, percent = percent, updatedAt = updatedAt))
    }

    fun getStored(bookId: String): ProgressEntity? = store.value[bookId]
}
