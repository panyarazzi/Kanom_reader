package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReaderHighlightEntity
import kotlinx.coroutines.flow.Flow

interface ReaderHighlightStore {
    fun observe(bookId: String): Flow<List<ReaderHighlightEntity>>
    suspend fun add(highlight: ReaderHighlightEntity): Long
    suspend fun remove(id: Long)
}
