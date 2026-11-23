package com.nextz.kanomtts.feature.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.BookWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RelinkCandidate(
    val uri: String,
    val name: String,
    val size: Long,
    val sha256: String?,
    val sourceFolder: String?,
)

internal fun relinkMatches(original: BookEntity, candidate: RelinkCandidate, extractedTitle: String?): Boolean {
    if (!original.sha256.isNullOrBlank()) return candidate.sha256 == original.sha256
    val fileTitle = candidate.name.substringBeforeLast('.', candidate.name)
    val titleMatches = extractedTitle.equals(original.title, true) || fileTitle.equals(original.title, true)
    val sizeMatches = original.fileSize <= 0L || candidate.size <= 0L || original.fileSize == candidate.size
    return titleMatches && sizeMatches
}

internal fun relinkCandidateWithFingerprint(
    original: BookEntity,
    candidate: RelinkCandidate,
    fingerprint: String?,
): RelinkCandidate? {
    if (original.sha256.isNullOrBlank()) return candidate
    val verified = candidate.copy(sha256 = fingerprint ?: candidate.sha256)
    return verified.takeIf { relinkMatches(original, it, null) }
}

data class RelinkUiState(
    val busy: Boolean = false,
    val bookId: String? = null,
    val candidates: List<RelinkCandidate> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class RelinkViewModel @Inject constructor(
    private val books: BookReader,
    private val writer: BookWriter,
    private val metadata: MetadataExtractor,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RelinkUiState())
    val uiState: StateFlow<RelinkUiState> = _uiState.asStateFlow()

    fun searchFile(bookId: String, uri: Uri) = search(bookId) {
        val document = DocumentFile.fromSingleUri(context, uri)
        val name = document?.name ?: uri.lastPathSegment.orEmpty()
        val size = document?.length() ?: 0L
        listOf(RelinkCandidate(uri.toString(), name, size, hash(uri), null))
    }

    fun searchFolder(bookId: String, treeUri: Uri) = search(bookId) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("ไม่สามารถเปิดโฟลเดอร์ที่เลือกได้")
        val found = mutableListOf<RelinkCandidate>()
        suspend fun walk(folder: DocumentFile, relative: String) {
            folder.listFiles().forEach { child ->
                if (child.isDirectory) {
                    val next = listOf(relative, child.name.orEmpty()).filter { it.isNotBlank() }.joinToString("/")
                    walk(child, next)
                } else if (child.isFile && child.name?.endsWith(".epub", true) == true) {
                    found += RelinkCandidate(child.uri.toString(), child.name.orEmpty(), child.length(),
                        null, listOf(root.name.orEmpty(), relative).filter { it.isNotBlank() }.joinToString("/"))
                }
            }
        }
        walk(root, "")
        found
    }

    private fun search(bookId: String, scan: suspend () -> List<RelinkCandidate>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = RelinkUiState(busy = true, bookId = bookId)
            val result = runCatching {
                val original = books.get(bookId) ?: error("ไม่พบข้อมูลหนังสือเดิม")
                val matches = scan().mapNotNull { candidate ->
                    if (!original.sha256.isNullOrBlank()) {
                        // SHA-256 is authoritative; do not reject a candidate based on provider size metadata.
                        // Keep the verified fingerprint on the returned candidate so confirm() persists it.
                        val candidateHash = candidate.sha256 ?: hash(Uri.parse(candidate.uri))
                        relinkCandidateWithFingerprint(original, candidate, candidateHash)
                    } else {
                        val sizePossible = original.fileSize <= 0L || candidate.size <= 0L ||
                            original.fileSize == candidate.size
                        if (!sizePossible) null else {
                            val extractedTitle = metadata.extract(candidate.uri)?.title
                            candidate.takeIf { relinkMatches(original, it, extractedTitle) }
                        }
                    }
                }
                matches
            }
            _uiState.value = result.fold(
                onSuccess = { matches ->
                    RelinkUiState(
                        bookId = bookId,
                        candidates = matches,
                        message = if (matches.isEmpty()) "ไม่พบไฟล์ที่ตรงกับหนังสือเดิม" else "พบไฟล์ที่ตรงกัน ${matches.size} รายการ กรุณาเลือกไฟล์ที่ถูกต้อง",
                    )
                },
                onFailure = { RelinkUiState(bookId = bookId, message = "ค้นหาไม่สำเร็จ: ${it.message}") },
            )
        }
    }

    fun clearResults() { _uiState.value = RelinkUiState() }

    fun confirm(candidate: RelinkCandidate, onSuccess: () -> Unit) {
        val bookId = _uiState.value.bookId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val original = books.get(bookId) ?: error("ไม่พบข้อมูลหนังสือเดิม")
                writer.updateSource(bookId, candidate.uri, candidate.sha256, candidate.sourceFolder ?: original.sourceFolder, candidate.name)
            }.getOrDefault(false)
            if (ok) {
                _uiState.value = RelinkUiState(message = "เชื่อมไฟล์ต้นฉบับสำเร็จ โดยคงรหัสหนังสือ ตำแหน่งอ่าน และ Bookmark เดิม")
                onSuccess()
            } else {
                _uiState.value = _uiState.value.copy(message = "เชื่อมไฟล์ไม่สำเร็จ")
            }
        }
    }

    private fun hash(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()
}
