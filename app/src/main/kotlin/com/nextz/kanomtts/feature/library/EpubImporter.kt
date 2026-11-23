package com.nextz.kanomtts.feature.library
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.repository.BookWriter
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
sealed interface ImportResult {
    object Duplicate : ImportResult
    data class Imported(val bookId: String) : ImportResult
    data class Relinked(val bookId: String) : ImportResult
    object Unreadable : ImportResult
    object EmptyFolder : ImportResult
    data class FolderSummary(
        val scanned: Int,
        val imported: Int,
        val duplicates: Int,
        val relinked: Int,
        val unreadable: Int,
    ) : ImportResult
}
class EpubImporter(
    private val extractor: MetadataExtractor,
    private val books: BookWriter,
    private val coversDir: File,
    private val now: () -> Long,
    private val contentHasher: suspend (String) -> String? = { null },
) {
    suspend fun import(uri: String, fileSize: Long, displayName: String?, sourceFolder: String? = null): ImportResult {
        // Re-importing a previously indexed SAF URI should not reopen or reparse the EPUB.
        if (books.isDuplicate(uri)) return ImportResult.Duplicate
        val meta = extractor.extract(uri) ?: return ImportResult.Unreadable
        val filenameTitle = displayName?.takeIf { it.isNotBlank() }?.let { name ->
            if (name.endsWith(".epub", ignoreCase = true)) name.dropLast(5) else name
        }
        val title = meta.title.ifBlank { filenameTitle ?: "Untitled" }
        // Matching by title and size can collide. Relinking is always explicit and user-confirmed.
        val sha256 = try {
            contentHasher(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val id = stableId(uri)
        val coverPath = meta.coverBytes?.let { bytes ->
            coversDir.mkdirs()
            File(coversDir, "$id.png").apply { writeBytes(bytes) }.absolutePath
        }
        books.add(BookEntity(
            id = id, uri = uri, title = title, author = meta.author, lang = meta.lang,
            coverPath = coverPath, fileSize = fileSize, addedAt = now(), lastOpenedAt = null,
            sha256 = sha256, sourceFolder = sourceFolder, sourceFileName = displayName,
        ))
        return ImportResult.Imported(id)
    }
    companion object {
        fun stableId(uri: String): String =
            MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
