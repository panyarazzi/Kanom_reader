package com.nextz.kanomtts.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.portable.PortableBackupSummary
import com.nextz.kanomtts.data.portable.PortableDataGateway
import com.nextz.kanomtts.data.portable.RestoreSourceCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstLaunchRecoveryViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val complete get() = File(context.noBackupFilesDir, "portable-recovery-complete-v1")
    private val pending get() = File(context.noBackupFilesDir, "portable-recovery-pending-v1")
    private val inProgress get() = File(context.noBackupFilesDir, "portable-recovery-in-progress-v1")

    private class FakeGateway : PortableDataGateway {
        var access = true
        var backupExists = true
        var failure: Throwable? = null
        var restoreCalls = 0
        private val summary = PortableBackupSummary(0, 0, 0, 0, 0, 0, 0, 0, "backup.zip")

        override fun hasStorageAccess() = access
        override fun ensurePortableFolders() = true
        override fun latestBackupExists() = backupExists
        override suspend fun createBackup() = summary
        override suspend fun previewBackupBooks(): List<BookEntity> = emptyList()
        override suspend fun restoreLatestBackup(
            sourceMappings: Map<String, RestoreSourceCandidate>,
        ): PortableBackupSummary {
            restoreCalls++
            failure?.let { throw it }
            return summary
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        clearMarkers()
    }

    @After
    fun tearDown() {
        clearMarkers()
        Dispatchers.resetMain()
    }

    private fun clearMarkers() {
        complete.delete()
        pending.delete()
        inProgress.delete()
    }

    @Test
    fun failedAutoRecoveryRemainsPendingAndCanCompleteOnRetry() = runTest {
        pending.parentFile?.mkdirs()
        pending.createNewFile()
        val gateway = FakeGateway().apply { failure = IllegalStateException("temporary restore failure") }
        val vm = FirstLaunchRecoveryViewModel(context, gateway)

        withTimeout(5_000) {
            vm.uiState.first { it.status == FirstLaunchRecoveryStatus.FAILED }
        }
        assertTrue(pending.exists())
        assertFalse(inProgress.exists())
        assertFalse(complete.exists())
        assertEquals(1, gateway.restoreCalls)

        gateway.failure = null
        vm.retryRestore()
        withTimeout(5_000) {
            vm.uiState.first { it.status == FirstLaunchRecoveryStatus.READY && complete.exists() }
        }
        assertEquals(2, gateway.restoreCalls)
        assertTrue(complete.exists())
        assertFalse(pending.exists())
        assertFalse(inProgress.exists())
    }

    @Test
    fun interruptedRecoveryMarkerCausesRestoreToResumeOnNextLaunch() = runTest {
        inProgress.parentFile?.mkdirs()
        inProgress.createNewFile()
        val gateway = FakeGateway()
        val vm = FirstLaunchRecoveryViewModel(context, gateway)

        withTimeout(5_000) {
            vm.uiState.first { it.status == FirstLaunchRecoveryStatus.READY && complete.exists() }
        }
        assertEquals(1, gateway.restoreCalls)
        assertFalse(pending.exists())
        assertFalse(inProgress.exists())
    }

    @Test
    fun missingStoragePermissionDoesNotClearPendingRecovery() = runTest {
        pending.parentFile?.mkdirs()
        pending.createNewFile()
        val gateway = FakeGateway().apply { access = false }
        val vm = FirstLaunchRecoveryViewModel(context, gateway)

        withTimeout(5_000) {
            vm.uiState.first { it.status == FirstLaunchRecoveryStatus.PERMISSION_REQUIRED }
        }
        assertTrue(pending.exists())
        assertFalse(complete.exists())
        assertEquals(0, gateway.restoreCalls)
    }
}
