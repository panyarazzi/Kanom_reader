package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow

interface ProgressReader {
    suspend fun get(bookId: String): ProgressEntity?
    fun observe(bookId: String): Flow<ProgressEntity?>
}
