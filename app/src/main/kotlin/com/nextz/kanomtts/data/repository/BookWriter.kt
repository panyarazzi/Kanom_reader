package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookEntity

interface BookWriter {
    suspend fun isDuplicate(uri: String, title: String, fileSize: Long): Boolean
    suspend fun add(book: BookEntity)
}
