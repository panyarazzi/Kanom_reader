package com.nextz.kanomtts.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.portable.PortableBackupSummary
import com.nextz.kanomtts.data.portable.PortableDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PortableDataUiState(
    val hasAccess: Boolean = false,
    val backupExists: Boolean = false,
    val busy: Boolean = false,
    val includeEpubFiles: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class PortableDataViewModel @Inject constructor(
    private val manager: PortableDataManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PortableDataUiState())
    val uiState: StateFlow<PortableDataUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val access = manager.hasStorageAccess()
        if (access) manager.ensurePortableFolders()
        _uiState.value = _uiState.value.copy(
            hasAccess = access,
            backupExists = manager.latestBackupExists(),
        )
    }

    fun setIncludeEpubFiles(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(includeEpubFiles = enabled)
    }

    fun createBackup() = runOperation {
        val result = manager.createBackup(includeEpubFiles = _uiState.value.includeEpubFiles)
        "สำรองข้อมูลสำเร็จ — หนังสือ ${result.books} เล่ม, ตำแหน่งอ่าน ${result.progress} รายการ, Bookmark ${result.bookmarks} รายการ, คัดลอก EPUB ${result.epubCopies} ไฟล์, คัดลอกไม่สำเร็จ ${result.epubCopyFailures} ไฟล์"
    }

    fun restoreBackup() = runOperation {
        val result = manager.restoreLatestBackup()
        "กู้คืนข้อมูลสำเร็จ — หนังสือ ${result.books} เล่ม, ตำแหน่งอ่าน ${result.progress} รายการ, Bookmark ${result.bookmarks} รายการ, ใช้ไฟล์ EPUB ใน Kanom ${result.epubCopies} ไฟล์"
    }

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
