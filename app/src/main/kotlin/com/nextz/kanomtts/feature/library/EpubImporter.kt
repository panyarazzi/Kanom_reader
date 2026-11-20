package com.nextz.kanomtts.feature.library

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.repository.BookWriter
import java.io.File
import java.security.MessageDigest

sealed interface ImportResult {
    object Duplicate : ImportResult
    data class Imported(val bookId: String) : ImportResult
    object Unreadable : ImportResult
}

class EpubImporter(
    private val extractor: MetadataExtractor,
    private val books: BookWriter,
    private val coversDir: File,
    private val now: () -> Long,
) {
    suspend fun import(uri: String, fileSize: Long, displayName: String?): ImportResult {
        val meta = extractor.extract(uri) ?: return ImportResult.Unreadable
        val title = meta.title.ifBlank { displayName?.removeSuffix(".epub") ?: "Untitled" }
        if (books.isDuplicate(uri, title, fileSize)) return ImportResult.Duplicate
        val id = stableId(uri)
        val coverPath = meta.coverBytes?.let { bytes ->
            coversDir.mkdirs()
            File(coversDir, "$id.png").apply { writeBytes(bytes) }.absolutePath
        }
        books.add(
            BookEntity(
                id = id,
                uri = uri,
                title = title,
                author = meta.author,
                lang = meta.lang,
                coverPath = coverPath,
                fileSize = fileSize,
                addedAt = now(),
                lastOpenedAt = null,
            )
        )
        return ImportResult.Imported(id)
    }

    companion object {
        fun stableId(uri: String): String =
            MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
