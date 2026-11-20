package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookEntity
import kotlinx.coroutines.flow.Flow

interface BookReader {
    fun observeLibrary(): Flow<List<BookEntity>>
    suspend fun get(id: String): BookEntity?
}
