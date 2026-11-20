package com.nextz.kanomtts.feature.replacement

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReplacementEditorViewModelTest {
    private lateinit var db: KanomDatabase
    private lateinit var repo: ReplacementRuleRepository

    @Before fun setUpVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Use a direct (inline) executor so Room queries run synchronously on the calling thread.
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(ctx, KanomDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        repo = ReplacementRuleRepository(db.replacementRuleDao())
    }
    @After fun tearDownVm() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun globalVm() = ReplacementEditorViewModel(SavedStateHandle(), repo)
    private fun bookVm(bookId: String) = ReplacementEditorViewModel(SavedStateHandle(mapOf("bookId" to bookId)), repo)

    @Test fun `addRule persists a valid global rule`() = runTest {
        val vm = globalVm()
        vm.uiState.first()
        vm.addRule("กรุงเทพ", "กฺรุง-เทบ", isRegex = false)
        val rules = repo.observeGlobal().first()
        assertEquals(1, rules.size)
        assertEquals("global", rules.first().scope)
        assertEquals("กฺรุง-เทบ", rules.first().replacement)
    }
    @Test fun `addRule rejects invalid regex`() = runTest {
        val vm = globalVm()
        vm.uiState.first()
        vm.addRule("[bad", "x", isRegex = true)
        assertEquals(0, repo.observeGlobal().first().size)
    }
    @Test fun `book scope writes book-scoped rules and exposes inherited global count`() = runTest {
        repo.add(com.nextz.kanomtts.data.db.ReplacementRuleEntity(scope="global", bookId=null, pattern="g", replacement="x", isRegex=false, enabled=true, position=0))
        val vm = bookVm("b1")
        vm.addRule("เล่ม", "เหฺล่ม", isRegex = false)
        val st = vm.uiState.first { it.rules.isNotEmpty() }
        assertEquals("book", st.rules.first().scope)
        assertEquals("b1", st.rules.first().bookId)
        assertEquals(1, st.inheritedGlobalCount)
        assertTrue(st.isBookScope)
    }
    @Test fun `toggleEnabled flips enabled`() = runTest {
        val vm = globalVm()
        vm.addRule("a", "b", isRegex = false)
        val rule = repo.observeGlobal().first().first()
        vm.toggleEnabled(rule)
        assertEquals(false, repo.observeGlobal().first().first().enabled)
    }
    @Test fun `reorder persists new order`() = runTest {
        val vm = globalVm()
        vm.addRule("a", "x", isRegex = false)
        vm.addRule("b", "x", isRegex = false)
        vm.uiState.first { it.rules.size == 2 }
        vm.reorder(from = 0, to = 1)
        assertEquals(listOf("b", "a"), repo.observeGlobal().first().map { it.pattern })
    }
    @Test fun `importTsv adds valid rules and exportTsv round-trips`() = runTest {
        val vm = globalVm()
        val tsv = "pattern\treplacement\tisRegex\tenabled\torder\nกข\tคง\tfalse\ttrue\t0"
        val result = vm.importTsv(tsv)
        assertEquals(TsvImportResult(imported = 1, skipped = 0), result)
        vm.uiState.first { it.rules.isNotEmpty() }
        assertTrue(vm.exportTsv().contains("กข\tคง"))
    }

    @Test fun `importTsv skips invalid-regex rows and persists only valid ones`() = runTest {
        val vm = globalVm()
        // One valid plain-text row + one invalid regex row.
        val tsv = "pattern\treplacement\tisRegex\tenabled\torder\n" +
            "กข\tคง\tfalse\ttrue\t0\n" +
            "[bad\tx\ttrue\ttrue\t1"
        val result = vm.importTsv(tsv)
        assertEquals(TsvImportResult(imported = 1, skipped = 1), result)
        val persisted = repo.observeGlobal().first()
        assertEquals(1, persisted.size)
        assertEquals("กข", persisted.first().pattern)
    }
}
