package com.nextz.kanomtts.feature.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.portable.PortableDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext context: Context,
    private val manager: PortableDataManager,
) : ViewModel() {
    private val startupState: SharedPreferences =
        context.getSharedPreferences(STARTUP_PREFS, Context.MODE_PRIVATE)

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
        // Bypass the existing-data guard: a previous attempt may have restored Room
        // successfully but failed before preferences were fully restored.
        startupState.edit()
            .putBoolean(KEY_RESTORE_IN_PROGRESS, true)
            .remove(KEY_COMPLETE)
            .apply()
        evaluate()
    }

    fun continueAfterFailure() {
        _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
    }

    private fun evaluate() {
        evaluationJob?.cancel()
        evaluationJob = viewModelScope.launch {
            _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.CHECKING)

            if (startupState.getBoolean(KEY_COMPLETE, false)) {
                _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
                return@launch
            }

            val restoreWasInterrupted = startupState.getBoolean(KEY_RESTORE_IN_PROGRESS, false)
            if (!restoreWasInterrupted && manager.hasLocalUserData()) {
                // Existing installation/update: never overwrite its live data on first run
                // of this startup-gate version.
                markComplete()
                _uiState.value = FirstLaunchRecoveryUiState(FirstLaunchRecoveryStatus.READY)
                return@launch
            }

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
            // If Android kills the process mid-restore, the next launch retries. Restore is
            // replacement-based and safe to repeat; this marker lives outside DataStore.
            startupState.edit().putBoolean(KEY_RESTORE_IN_PROGRESS, true).apply()

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

    private fun markComplete() {
        startupState.edit()
            .putBoolean(KEY_COMPLETE, true)
            .remove(KEY_RESTORE_IN_PROGRESS)
            .apply()
    }

    private companion object {
        const val STARTUP_PREFS = "kanom_portable_startup"
        const val KEY_COMPLETE = "first_launch_recovery_complete_v1"
        const val KEY_RESTORE_IN_PROGRESS = "first_launch_restore_in_progress_v1"
    }
}
