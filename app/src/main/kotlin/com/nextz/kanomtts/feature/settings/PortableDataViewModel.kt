package com.nextz.kanomtts.feature.settings

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.portable.PortableBackupSummary
import com.nextz.kanomtts.data.portable.PortableDataManager
import com.nextz.kanomtts.data.portable.RestoreSourceCandidate
import com.nextz.kanomtts.data.portable.RestoreSourcePlan
import com.nextz.kanomtts.data.portable.buildRestoreSourcePlan
import com.nextz.kanomtts.feature.library.MetadataExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RestoreReconciliationStage {
    NONE,
    CHOOSE_FOLDER,
    REVIEW_UNIQUE,
    AMBIGUOUS,
    NOT_FOUND,
}

data class PortableDataUiState(
    val hasAccess: Boolean = false,
    val backupExists: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val restoreStage: RestoreReconciliationStage = RestoreReconciliationStage.NONE,
    val restorePlan: RestoreSourcePlan? = null,
    val ambiguousIndex: Int = 0,
)

@HiltViewModel
class PortableDataViewModel @Inject constructor(
    private val manager: PortableDataManager,
    private val metadata: MetadataExtractor,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PortableDataUiState())
    val uiState: StateFlow<PortableDataUiState> = _uiState.asStateFlow()
    private val selectedSources = linkedMapOf<String, RestoreSourceCandidate>()
    private var activeRestoreJob: Job? = null
    private var previewBooks = emptyList<com.nextz.kanomtts.data.db.BookEntity>()

    init { refresh() }

    fun refresh() {
        val access = manager.hasStorageAccess()
        if (access) manager.ensurePortableFolders()
        _uiState.value = _uiState.value.copy(
            hasAccess = access,
            backupExists = manager.latestBackupExists(),
        )
    }

    fun createBackup() = runOperation {
        val result = manager.createBackup()
        backupSummary("สำรองข้อมูลสำเร็จ", result)
    }

    /** Start a read-only preflight. The live database is not changed here. */
    fun prepareRestore() {
        if (_uiState.value.busy) return
        activeRestoreJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busy = true, message = null, restorePlan = null)
            selectedSources.clear()
            val result = runCatching { manager.previewBackupBooks() }
            result.onSuccess { books ->
                previewBooks = books
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    restoreStage = RestoreReconciliationStage.CHOOSE_FOLDER,
                    message = "เลือกโฟลเดอร์หลักที่เก็บ EPUB ต้นฉบับ ระบบจะค้นหาในโฟลเดอร์ย่อยให้อัตโนมัติ",
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    restoreStage = RestoreReconciliationStage.NONE,
                    message = if (it is CancellationException) "ยกเลิกการเตรียม Restore แล้ว" else "อ่านข้อมูลสำรองไม่สำเร็จ: ${it.message}",
                )
                if (it is CancellationException) activeRestoreJob = null
            }
        }
    }

    /** Recursively scan the selected SAF tree and build a conservative review plan. */
    fun scanRestoreFolder(treeUri: Uri) {
        if (_uiState.value.busy || previewBooks.isEmpty()) return
        activeRestoreJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busy = true, message = "กำลังค้นหา EPUB ในโฟลเดอร์และโฟลเดอร์ย่อย…")
            val result = runCatching {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                    ?: error("ไม่สามารถเปิดโฟลเดอร์ที่เลือกได้")
                val needsHashes = previewBooks.any { !it.sha256.isNullOrBlank() }
                val legacyBooks = previewBooks.filter { it.sha256.isNullOrBlank() }
                val found = mutableListOf<RestoreSourceCandidate>()
                suspend fun walk(folder: DocumentFile, relative: String) {
                    currentCoroutineContext().ensureActive()
                    folder.listFiles().forEach { child ->
                        currentCoroutineContext().ensureActive()
                        if (child.isDirectory) {
                            val next = listOf(relative, child.name.orEmpty())
                                .filter { it.isNotBlank() }.joinToString("/")
                            walk(child, next)
                        } else if (child.isFile && child.name?.endsWith(".epub", true) == true) {
                            val size = child.length()
                            val uri = child.uri
                            val hash = if (needsHashes) hash(uri) else null
                            val needsMetadata = legacyBooks.any { book ->
                                book.fileSize > 0L && size > 0L && book.fileSize == size
                            }
                            val title = if (needsMetadata) metadata.extract(uri.toString())?.title else null
                            found += RestoreSourceCandidate(
                                uri = uri.toString(),
                                name = child.name.orEmpty(),
                                size = size,
                                sha256 = hash,
                                sourceFolder = listOf(root.name.orEmpty(), relative)
                                    .filter { it.isNotBlank() }.joinToString("/"),
                                extractedTitle = title,
                            )
                        }
                    }
                }
                walk(root, "")
                buildRestoreSourcePlan(previewBooks, found)
            }
            result.onSuccess { plan ->
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    restorePlan = plan,
                    restoreStage = RestoreReconciliationStage.REVIEW_UNIQUE,
                    message = "ตรวจพบ EPUB ที่จับคู่ได้แน่นอน ${plan.uniqueMatches.size} เล่ม, ต้องเลือกเอง ${plan.ambiguousMatches.size} เล่ม, ไม่พบ ${plan.notFound.size} เล่ม",
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    restoreStage = RestoreReconciliationStage.CHOOSE_FOLDER,
                    restorePlan = null,
                    message = if (it is CancellationException) "ยกเลิกการค้นหาโฟลเดอร์แล้ว" else "ค้นหาโฟลเดอร์ไม่สำเร็จ: ${it.message}",
                )
                if (it is CancellationException) activeRestoreJob = null
            }
        }
    }

    /** Confirm the entire unique-match batch before any ambiguous choice is requested. */
    fun confirmUniqueMatches() {
        val plan = _uiState.value.restorePlan ?: return
        plan.uniqueMatches.forEach { (book, candidate) -> selectedSources[book.id] = candidate }
        _uiState.value = _uiState.value.copy(
            restoreStage = if (plan.ambiguousMatches.isEmpty()) RestoreReconciliationStage.NOT_FOUND
                else RestoreReconciliationStage.AMBIGUOUS,
            ambiguousIndex = 0,
            message = null,
        )
    }

    fun chooseAmbiguousCandidate(candidate: RestoreSourceCandidate) {
        val plan = _uiState.value.restorePlan ?: return
        val item = plan.ambiguousMatches.getOrNull(_uiState.value.ambiguousIndex) ?: return
        selectedSources[item.book.id] = candidate
        advanceAmbiguous(plan)
    }

    fun skipAmbiguousBook() {
        val plan = _uiState.value.restorePlan ?: return
        advanceAmbiguous(plan)
    }

    private fun advanceAmbiguous(plan: RestoreSourcePlan) {
        val next = _uiState.value.ambiguousIndex + 1
        _uiState.value = _uiState.value.copy(
            ambiguousIndex = next,
            restoreStage = if (next >= plan.ambiguousMatches.size) RestoreReconciliationStage.NOT_FOUND
                else RestoreReconciliationStage.AMBIGUOUS,
        )
    }

    /** Not-found books are intentionally left unresolved; then apply the staged restore. */
    fun finishRestore() {
        if (_uiState.value.busy) return
        activeRestoreJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busy = true, message = "กำลังกู้คืนข้อมูล…")
            val result = runCatching { manager.restoreLatestBackup(selectedSources.toMap()) }
            result.onSuccess { summary ->
                selectedSources.clear()
                previewBooks = emptyList()
                _uiState.value = _uiState.value.copy(
                    hasAccess = manager.hasStorageAccess(),
                    backupExists = manager.latestBackupExists(),
                    busy = false,
                    restoreStage = RestoreReconciliationStage.NONE,
                    restorePlan = null,
                    message = backupSummary("กู้คืนข้อมูลสำเร็จ", summary),
                )
            }.onFailure {
                if (it is CancellationException) {
                    selectedSources.clear()
                    previewBooks = emptyList()
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        restoreStage = RestoreReconciliationStage.NONE,
                        restorePlan = null,
                        ambiguousIndex = 0,
                        message = "ยกเลิกการกู้คืนแล้ว ข้อมูลเดิมยังคงอยู่",
                    )
                    activeRestoreJob = null
                } else {
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        message = "กู้คืนไม่สำเร็จ ข้อมูลเดิมยังไม่ถูกแทนที่: ${it.message}",
                    )
                }
            }
        }
    }

    fun cancelRestore() {
        val running = activeRestoreJob
        if (running?.isActive == true) {
            // Keep the operation marked busy until its cancellation has unwound. The manager
            // checks cancellation before the Room transaction and within its restore stages.
            _uiState.value = _uiState.value.copy(busy = true, message = "กำลังยกเลิกการกู้คืน…")
            running.cancel()
            return
        }
        activeRestoreJob = null
        selectedSources.clear()
        previewBooks = emptyList()
        _uiState.value = _uiState.value.copy(
            busy = false,
            restoreStage = RestoreReconciliationStage.NONE,
            restorePlan = null,
            ambiguousIndex = 0,
            message = "ยกเลิกการกู้คืนแล้ว ข้อมูลปัจจุบันไม่ได้เปลี่ยนแปลง",
        )
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

    private fun backupSummary(label: String, result: PortableBackupSummary): String =
        "$label — หนังสือ ${result.books} เล่ม, ตำแหน่งอ่าน ${result.progress} รายการ, Bookmark ${result.bookmarks} รายการ, กฎแทนที่ ${result.replacementRules} รายการ, เสียงพากย์ ${result.voiceConfigs} รายการ, Tags ${result.tags} รายการ, Notes ${result.notes} รายการ, Reading Sessions ${result.readingSessions} รายการ"

    private fun runOperation(block: suspend () -> String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, message = null)
            val message = runCatching { block() }.fold(
                onSuccess = { it },
                onFailure = { "ดำเนินการไม่สำเร็จ: ${it.message ?: "เกิดข้อผิดพลาด"}" },
            )
            _uiState.value = _uiState.value.copy(
                hasAccess = manager.hasStorageAccess(),
                backupExists = manager.latestBackupExists(),
                busy = false,
                message = message,
            )
        }
    }
}
