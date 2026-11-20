package com.nextz.kanomtts.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.portable.PortableDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FirstLaunchRecoveryStatus {
    CHECKING,
    PERMISSION_REQUIRED,
    RESTORING,
    FAILED,
    READY,
}

data class FirstLaunchRecoveryUiState(
    val status: FirstLaunchRecoveryStatus = FirstLaunchRecoveryStatus.CHECKING,
    val errorMessage: String? = null,
)

@HiltViewModel
class FirstLaunchRecoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: PortableDataManager,
) : ViewModel() {
    // noBackupFilesDir survives app updates but is not restored by Android Auto Backup.
    // This lets a true reinstall be distinguished from an ordinary app update.
    private val completeMarker = File(context.noBackupFilesDir, "portable-recovery-complete-v1")
    private val pendingMarker = File(context.noBackupFilesDir, "portable-recovery-pending-v1")
    private val inProgressMarker = File(context.noBackupFilesDir, "portable-recovery-in-progress-v1")

    private val _uiState = MutableStateFlow(FirstLaunchRecoveryUiState())
    val uiState: StateFlow<FirstLaunchRecoveryUiState> = _uiState.asStateFlow()

    private var evaluationJob: Job? = null

    init {
        evaluate()
    }

    fun onPermissionResult() {
        evaluate()
    }

    fun retryRestore() {
        completeMarker.delete()
        pendingMarker.parentFile?.mkdirs()
        pendingMarker.createNewFile()
        inProgressMarker.delete()
        evaluate()
    }

    fun continueAfterFailure() {
        _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
    }

    fun continueWithoutPermission() {
        // Keep pendingMarker so a later cold start can resume recovery even if the
        // installation receives an app update before the user grants permission.
        pendingMarker.parentFile?.mkdirs()
        pendingMarker.createNewFile()
        _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
    }

    private fun evaluate() {
        evaluationJob?.cancel()
        evaluationJob = viewModelScope.launch {
            _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.CHECKING)

            if (completeMarker.exists()) {
                _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
                return@launch
            }

            val recoveryWasPending = pendingMarker.exists() || inProgressMarker.exists()
            if (!recoveryWasPending && !isFreshInstall()) {
                // First rollout of this feature on an existing installation: do not replace
                // its live app data with an older portable snapshot.
                markComplete()
                _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
                return@launch
            }

            // Persist the fresh-install decision before requesting permission. If the user
            // returns later, or updates the APK first, the recovery flow remains pending.
            pendingMarker.parentFile?.mkdirs()
            pendingMarker.createNewFile()

            if (!manager.hasStorageAccess()) {
                _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.PERMISSION_REQUIRED)
                return@launch
            }

            if (!manager.latestBackupExists()) {
                markComplete()
                _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
                return@launch
            }

            _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.RESTORING)
            inProgressMarker.createNewFile()

            runCatching { manager.restoreLatestBackup() }
                .onSuccess {
                    markComplete()
                    _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
                }
                .onFailure { error ->
                    markComplete()
                    _uiState.value = FirstLaunchRecoveryUiState(
                        status = FirstLaunchRecoveryStatus.FAILED,
                        errorMessage = error.message ?: "เกิดข้อผิดพลาดที่ไม่ทราบสาเหตุ",
                    )
                }
        }
    }

    private fun isFreshInstall(): Boolean {
        val packageInfo = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return false

        // On reinstall, Android assigns firstInstallTime and lastUpdateTime together.
        // On an in-place app update, firstInstallTime remains older than lastUpdateTime.
        return packageInfo.lastUpdateTime - packageInfo.firstInstallTime <= FRESH_INSTALL_WINDOW_MS
    }

    private fun markComplete() {
        completeMarker.parentFile?.mkdirs()
        completeMarker.createNewFile()
        pendingMarker.delete()
        inProgressMarker.delete()
    }

    private companion object {
        const val FRESH_INSTALL_WINDOW_MS = 5_000L
    }
}
