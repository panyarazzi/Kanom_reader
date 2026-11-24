package com.nextz.kanomtts.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.portable.PortableBackupSummary
import com.nextz.kanomtts.data.portable.PortableDataGateway
import com.nextz.kanomtts.data.portable.RestoreBookMatches
import com.nextz.kanomtts.data.portable.RestoreSourceCandidate
import com.nextz.kanomtts.data.portable.RestoreSourcePlan
import com.nextz.kanomtts.feature.library.BookMetadata
import com.nextz.kanomtts.feature.library.MetadataExtractor
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PortableDataViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    private class FakeGateway : PortableDataGateway {
        var restoredMappings: Map<String, RestoreSourceCandidate>? = null
        var failure: Throwable? = null
        private val summary = PortableBackupSummary(3, 2, 0, 0, 0, 0, 0, 0, "backup.zip")

        override fun hasStorageAccess() = true
        override fun ensurePortableFolders() = true
        override fun latestBackupExists() = true
        override suspend fun createBackup() = summary
        override suspend fun previewBackupBooks() = emptyList<BookEntity>()
        override suspend fun restoreLatestBackup(
            sourceMappings: Map<String, RestoreSourceCandidate>,
        ): PortableBackupSummary {
            restoredMappings = sourceMappings
            failure?.let { throw it }
            return summary
        }
    }

    private val metadata = object : MetadataExtractor {
        override suspend fun extract(uri: String): BookMetadata? = null
    }

    private fun book(id: String) = BookEntity(
        id = id,
        uri = "content://old/$id.epub",
        title = "Book $id",
        author = null,
        lang = "th",
        coverPath = null,
        fileSize = 100L,
        addedAt = 1L,
        lastOpenedAt = null,
        sha256 = "hash-$id",
        sourceFileName = "$id.epub",
    )

    private fun candidate(name: String, uri: String = "content://new/$name") =
        RestoreSourceCandidate(uri, name, 100L, null, "Books", null)

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(gateway: FakeGateway) = PortableDataViewModel(
        gateway,
        metadata,
        ApplicationProvider.getApplicationContext<Context>(),
    )

    @Test
    fun selectingAmbiguousCandidateAdvancesOnceAndRestoresConfirmedMappings() = runTest {
        val gateway = FakeGateway()
        val vm = viewModel(gateway)
        val uniqueBook = book("unique")
        val ambiguousBook = book("ambiguous")
        val missingBook = book("missing")
        val uniqueSource = candidate("unique.epub")
        val selectedSource = candidate("ambiguous-copy.epub")
        val plan = RestoreSourcePlan(
            uniqueMatches = listOf(uniqueBook to uniqueSource),
            ambiguousMatches = listOf(
                RestoreBookMatches(ambiguousBook, listOf(selectedSource, candidate("ambiguous.epub", "content://new/other.epub"))),
            ),
            notFound = listOf(missingBook),
        )

        vm.acceptRestorePlan(plan)
        vm.confirmUniqueMatches()
        assertEquals(RestoreReconciliationStage.AMBIGUOUS, vm.uiState.value.restoreStage)
        assertEquals(listOf("missing"), vm.uiState.value.unresolvedBooks.map { it.id })

        vm.chooseAmbiguousCandidate(selectedSource)
        assertEquals(RestoreReconciliationStage.NOT_FOUND, vm.uiState.value.restoreStage)
        assertEquals(0, vm.uiState.value.ambiguousIndex)

        vm.finishRestore()
        withTimeout(5_000) {
            vm.uiState.first {
                it.restoreStage == RestoreReconciliationStage.NONE &&
                    it.message?.startsWith("กู้คืนข้อมูลสำเร็จ") == true
            }
        }
        assertEquals(setOf("unique", "ambiguous"), gateway.restoredMappings?.keys)
        assertEquals(uniqueSource.uri, gateway.restoredMappings?.get("unique")?.uri)
        assertEquals(selectedSource.uri, gateway.restoredMappings?.get("ambiguous")?.uri)
    }

    @Test
    fun skippingAmbiguousBookKeepsItUnresolvedAndDoesNotMapItsSource() = runTest {
        val gateway = FakeGateway()
        val vm = viewModel(gateway)
        val ambiguousBook = book("ambiguous")
        vm.acceptRestorePlan(
            RestoreSourcePlan(
                uniqueMatches = emptyList(),
                ambiguousMatches = listOf(
                    RestoreBookMatches(ambiguousBook, listOf(candidate("one.epub"), candidate("two.epub"))),
                ),
                notFound = emptyList(),
            ),
        )

        vm.confirmUniqueMatches()
        vm.skipAmbiguousBook()
        assertEquals(RestoreReconciliationStage.NOT_FOUND, vm.uiState.value.restoreStage)
        assertEquals(listOf("ambiguous"), vm.uiState.value.unresolvedBooks.map { it.id })

        vm.finishRestore()
        withTimeout(5_000) {
            vm.uiState.first {
                it.restoreStage == RestoreReconciliationStage.NONE &&
                    it.message?.startsWith("กู้คืนข้อมูลสำเร็จ") == true
            }
        }
        assertTrue(gateway.restoredMappings.orEmpty().isEmpty())
    }

    @Test
    fun alreadyAssignedUniqueSourceCannotBeReusedForAmbiguousBook() = runTest {
        val vm = viewModel(FakeGateway())
        val shared = candidate("same.epub")
        vm.acceptRestorePlan(
            RestoreSourcePlan(
                uniqueMatches = listOf(book("unique") to shared),
                ambiguousMatches = listOf(RestoreBookMatches(book("ambiguous"), listOf(shared))),
                notFound = emptyList(),
            ),
        )

        vm.confirmUniqueMatches()
        assertTrue(vm.isCandidateAlreadyAssigned(shared))
        vm.chooseAmbiguousCandidate(shared)

        assertEquals(RestoreReconciliationStage.AMBIGUOUS, vm.uiState.value.restoreStage)
        assertTrue(vm.uiState.value.message.orEmpty().contains("จับคู่กับหนังสือเล่มอื่น"))
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun failedRestoreKeepsReviewStateSoUserCanRetryWithoutLosingMappings() = runTest {
        val gateway = FakeGateway().apply { failure = IllegalStateException("simulated failure") }
        val vm = viewModel(gateway)
        val source = candidate("retry.epub")
        vm.acceptRestorePlan(
            RestoreSourcePlan(
                uniqueMatches = listOf(book("retry") to source),
                ambiguousMatches = emptyList(),
                notFound = emptyList(),
            ),
        )
        vm.confirmUniqueMatches()
        vm.finishRestore()

        withTimeout(5_000) {
            vm.uiState.first { !it.busy && it.restoreStage == RestoreReconciliationStage.NOT_FOUND }
        }
        assertEquals(source.uri, gateway.restoredMappings?.get("retry")?.uri)
        assertTrue(vm.uiState.value.message.orEmpty().startsWith("กู้คืนไม่สำเร็จ"))
    }
}
