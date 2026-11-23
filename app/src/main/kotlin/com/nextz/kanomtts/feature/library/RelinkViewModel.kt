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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    if (!original.sha256.isNullOrBlank()) {
        return candidate.sha256?.equals(original.sha256, ignoreCase = true) == true
    }
    val fileTitle = candidate.name.substringBeforeLast('.', candidate.name)
    val titleMatches = extractedTitle.equals(original.title, true) || fileTitle.equals(original.title, true)
    val filenameMatches = !original.sourceFileName.isNullOrBlank() &&
        candidate.name.equals(original.sourceFileName, ignoreCase = true)
    val sizeKnown = original.fileSize > 0L && candidate.size > 0L
    val sizeMatches = !sizeKnown || original.fileSize == candidate.size
    return sizeMatches && (filenameMatches || titleMatches)
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
            currentCoroutineContext().ensureActive()
            folder.listFiles().forEach { child ->
                currentCoroutineContext().ensureActive()
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
        if (_uiState.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = RelinkUiState(busy = true, bookId = bookId)
            try {
                val original = books.get(bookId) ?: error("ไม่พบข้อมูลหนังสือเดิม")
                val matches = scan().mapNotNull { candidate ->
                    currentCoroutineContext().ensureActive()
                    if (!original.sha256.isNullOrBlank()) {
                        // SHA-256 is authoritative; provider size metadata is only advisory.
                        val candidateHash = candidate.sha256 ?: hash(Uri.parse(candidate.uri))
                        relinkCandidateWithFingerprint(original, candidate, candidateHash)
                    } else {
                        val sizePossible = original.fileSize > 0L && candidate.size > 0L &&
                            original.fileSize == candidate.size
                        if (!sizePossible) null else {
                            val extractedTitle = metadata.extract(candidate.uri)?.title
                            candidate.takeIf { relinkMatches(original, it, extractedTitle) }
                        }
                    }
                }
                _uiState.value = RelinkUiState(
                    bookId = bookId,
                    candidates = matches,
                    message = if (matches.isEmpty()) "ไม่พบไฟล์ที่ตรงกับหนังสือเดิม"
                        else "พบไฟล์ที่ตรงกัน ${matches.size} รายการ กรุณาเลือกไฟล์ที่ถูกต้อง",
                )
            } catch (cancelled: CancellationException) {
                _uiState.value = RelinkUiState(bookId = bookId, message = "ยกเลิกการค้นหาไฟล์แล้ว")
            } catch (failure: Exception) {
                _uiState.value = RelinkUiState(bookId = bookId, message = "ค้นหาไม่สำเร็จ: ${failure.message}")
            }
        }
    }

    fun clearResults() { _uiState.value = RelinkUiState() }

    fun confirm(candidate: RelinkCandidate, onSuccess: () -> Unit) {
        if (_uiState.value.busy) return
        val bookId = _uiState.value.bookId ?: return
        if (_uiState.value.candidates.none { it.uri == candidate.uri }) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busy = true, message = "กำลังตรวจสอบไฟล์ต้นฉบับอีกครั้ง…")
            try {
                val original = books.get(bookId) ?: error("ไม่พบข้อมูลหนังสือเดิม")
                val uri = Uri.parse(candidate.uri)
                val actualHash = hash(uri)
                if (!original.sha256.isNullOrBlank()) {
                    require(actualHash?.equals(original.sha256, ignoreCase = true) == true) {
                        "SHA-256 ของไฟล์ไม่ตรงกับหนังสือเดิม หรือไม่สามารถอ่านไฟล์ได้"
                    }
                } else {
                    val currentSize = DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
                    val refreshedCandidate = candidate.copy(size = currentSize, sha256 = actualHash)
                    val extractedTitle = metadata.extract(candidate.uri)?.title
                    require(relinkMatches(original, refreshedCandidate, extractedTitle)) {
                        "ชื่อหรือขนาดไฟล์ไม่ตรงกับหนังสือเดิม"
                    }
                }
                val ok = writer.updateSource(
                    bookId,
                    candidate.uri,
                    actualHash ?: original.sha256,
                    candidate.sourceFolder ?: original.sourceFolder,
                    candidate.name,
                )
                check(ok) { "บันทึกตำแหน่งไฟล์ใหม่ไม่สำเร็จ" }
                _uiState.value = RelinkUiState(
                    message = "เชื่อมไฟล์ต้นฉบับสำเร็จ โดยคงรหัสหนังสือ ตำแหน่งอ่าน และ Bookmark เดิม",
                )
                onSuccess()
            } catch (cancelled: CancellationException) {
                _uiState.value = _uiState.value.copy(busy = false, message = "ยกเลิกการเชื่อมไฟล์แล้ว")
            } catch (failure: Exception) {
                _uiState.value = _uiState.value.copy(busy = false, message = "เชื่อมไฟล์ไม่สำเร็จ: ${failure.message}")
            }
        }
    }

    private suspend fun hash(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
