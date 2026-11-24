package com.nextz.kanomtts.feature.library.util

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.BookWriter
import com.nextz.kanomtts.data.repository.ProgressReader
import com.nextz.kanomtts.feature.library.BookMetadata
import com.nextz.kanomtts.feature.library.MetadataExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBookRepository : BookWriter, BookReader {
    val books = MutableStateFlow<List<BookEntity>>(emptyList())
    override fun observeLibrary(): Flow<List<BookEntity>> = books
    override suspend fun get(id: String): BookEntity? = books.value.find { it.id == id }
    override suspend fun add(book: BookEntity) { books.value = books.value.filterNot { it.id == book.id } + book }
    override suspend fun isDuplicate(uri: String): Boolean = books.value.any { it.uri == uri }
    override suspend fun findBySha256(sha256: String): BookEntity? =
        books.value.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
    override suspend fun updateSource(
        bookId: String,
        uri: String,
        sha256: String?,
        sourceFolder: String?,
        sourceFileName: String?,
    ): Boolean {
        val current = books.value.find { it.id == bookId } ?: return false
        books.value = books.value.map { book ->
            if (book.id == bookId) book.copy(
                uri = uri,
                sha256 = sha256 ?: book.sha256,
                sourceFolder = sourceFolder,
                sourceFileName = sourceFileName,
            ) else book
        }
        return current.id == bookId
    }
    suspend fun remove(id: String) { books.value = books.value.filterNot { it.id == id } }
}

class FakeProgressRepository : ProgressReader {
    val progressMap = MutableStateFlow<Map<String, ProgressEntity?>>(emptyMap())

    fun setProgress(bookId: String, progress: ProgressEntity?) {
        progressMap.value = progressMap.value + (bookId to progress)
    }

    override suspend fun get(bookId: String): ProgressEntity? = progressMap.value[bookId]

    override fun observe(bookId: String): Flow<ProgressEntity?> =
        progressMap.map { it[bookId] }
}

class FakeMetadataExtractor(var result: BookMetadata?) : MetadataExtractor {
    var lastUri: String? = null
    override suspend fun extract(uri: String): BookMetadata? { lastUri = uri; return result }
}
