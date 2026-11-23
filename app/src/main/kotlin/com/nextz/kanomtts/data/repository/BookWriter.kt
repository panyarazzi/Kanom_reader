package com.nextz.kanomtts.data.repository
import com.nextz.kanomtts.data.db.BookEntity
interface BookWriter {
    suspend fun isDuplicate(uri: String): Boolean
    suspend fun add(book: BookEntity)
    suspend fun updateSource(bookId: String, uri: String, sha256: String?, sourceFolder: String?, sourceFileName: String?): Boolean = false
}
