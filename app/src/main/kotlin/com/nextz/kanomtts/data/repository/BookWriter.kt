package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookEntity

interface BookWriter {
    suspend fun isDuplicate(uri: String, title: String, fileSize: Long): Boolean
    suspend fun add(book: BookEntity)

    /** Reconnect a restored book to a newly selected source while preserving its stable ID and reading data. */
    suspend fun relinkSource(uri: String, title: String, fileSize: Long): String? = null
}
