# Plan 09a — Replacement Editor + Add-Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the rule-editor UI + Add-Replacement-from-selection so the already-wired pre-TTS text-replacement engine has rules to apply (the table is empty today).

**Architecture:** Pre-TTS application already exists (`DefaultTtsContentSource` → `ReplacementEngine` → `TextPipeline`) and is unchanged (apply-on-next-start). This plan adds: pure validation/reorder/mapper helpers; three new DAO/repo methods; a `ReplacementEditorViewModel` + dedicated editor screen (reached from a Settings row and, scoped to a book, from the reader settings sheet); TSV import/export via SAF; and the reader Add-Replacement quick-add sheet.

**Tech Stack:** Kotlin, Compose + Material 3 Expressive, Hilt, Room 2.8.4, Coroutines/Flow, Storage Access Framework, JUnit4 + Robolectric (`@Config(sdk=[35])`), the existing `:text:replacement` engine + TSV codec.

## Global Constraints

- **Toolchain:** AGP 9.2.1 BUILT-IN Kotlin 2.2.10 — do NOT add Kotlin plugins to `:app`; `src/main/kotlin` compiles for free. Java 17 + desugaring. Compose BOM `2026.06.00`. Hilt via KSP. Room 2.8.4.
- **No reorderable-list dependency exists** in `gradle/libs.versions.toml`, and the bleeding-edge toolchain makes adding one risky — **drag-reorder is hand-rolled** (Task 5). Do NOT add a new dependency.
- **Regex validation before save is mandatory:** `ReplacementEngine` compiles `Regex(pattern)` for every enabled regex rule **in its constructor**, which runs at the next playback stream start. An invalid saved regex would throw there and **crash playback**. The editor and quick-add MUST block save on `ReplacementRuleValidation.validate(...) != null`.
- **Apply timing = on next playback start.** Do NOT change `DefaultTtsContentSource` (it snapshots `rules.observeEffective(bookId).first()` at stream start). Rule edits take effect when playback (re)starts.
- **Selection text:** the reader selection carries ZWSP (U+200B); always `stripZwsp(...).trim()` it before use (the helper exists in `ReaderViewModel`).
- **Scope values:** `ReplacementRuleEntity.scope` is the string `"global"` or `"book"`; `bookId` is non-null only when `scope == "book"`.
- **Tests:** `:app` unit/Robolectric tests live in `app/src/test/kotlin/...`; JUnit4 (`org.junit.Test`, `org.junit.Assert.*`), backtick names. Room/Android tests use `RoomTestBase` (`@Config(sdk=[35]) @RunWith(RobolectricTestRunner)`, in-memory DB). VM tests that touch `viewModelScope` drive the dispatcher via `Dispatchers.setMain(UnconfinedTestDispatcher())` / `resetMain()` (the Plan-08a pattern). Commands: `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`, `./gradlew :app:lintDebug`.
- **Default Thai pack, Visual Replacement, live-apply are OUT of 09a scope.**

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleValidation.kt` — `RuleError` + `validate` (Task 1).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementReorder.kt` — `reassignPositions` (Task 1).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorViewModel.kt` — VM + `ReplacementEditorUiState` (Task 3).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt` — editor screen (Task 4) + drag-reorder (Task 5).
- `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleSheet.kt` — add/edit sheet (Task 4).
- Tests under `app/src/test/kotlin/com/nextz/kanomtts/...` per task.

**Modify:**
- `data/db/ReplacementRuleDao.kt` — `observeByBook`, `globalCount`, `updateAll` (Task 2).
- `data/repository/ReplacementRuleRepository.kt` — `observeByBook`, `globalCount`, `reorder` (Task 2).
- `data/repository/RuleMapper.kt` — `toEntity` (Task 1).
- `ui/screens/SettingsScreen.kt` + `ui/navigation/KanomApp.kt` — Replacements row + the two routes (Task 4).
- `reader/ReaderViewModel.kt`, `reader/ReaderScreen.kt`, `reader/ReaderSettingsSheet.kt` — Add-Replacement + manage row (Task 6).
- `res/values/strings.xml` — new strings (Tasks 4, 6).

---

## Task 1: Pure helpers — validation, reorder, mapper (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleValidation.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementReorder.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/repository/RuleMapper.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleValidationTest.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementReorderTest.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/repository/RuleMapperTest.kt`

**Interfaces:**
- Consumes: `ReplacementRuleEntity` (`data.db`), `ReplacementRule` (`text.replacement`).
- Produces: `enum RuleError { EmptyPattern, InvalidRegex }`; `ReplacementRuleValidation.validate(pattern: String, isRegex: Boolean): RuleError?`; `ReplacementReorder.reassignPositions(rules: List<ReplacementRuleEntity>, from: Int, to: Int): List<ReplacementRuleEntity>`; `RuleMapper.toEntity(rule: ReplacementRule, scope: String, bookId: String?, id: Long = 0): ReplacementRuleEntity`.

- [ ] **Step 1: Write the failing tests**

`ReplacementRuleValidationTest.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplacementRuleValidationTest {
    @Test fun `blank pattern is EmptyPattern`() {
        assertEquals(RuleError.EmptyPattern, ReplacementRuleValidation.validate("  ", isRegex = false))
        assertEquals(RuleError.EmptyPattern, ReplacementRuleValidation.validate("", isRegex = true))
    }
    @Test fun `valid literal and regex pass`() {
        assertNull(ReplacementRuleValidation.validate("กรุงเทพ", isRegex = false))
        assertNull(ReplacementRuleValidation.validate("\\d+", isRegex = true))
    }
    @Test fun `invalid regex is InvalidRegex but only when isRegex`() {
        assertEquals(RuleError.InvalidRegex, ReplacementRuleValidation.validate("[unclosed", isRegex = true))
        assertNull(ReplacementRuleValidation.validate("[unclosed", isRegex = false)) // literal: any string is valid
    }
}
```

`ReplacementReorderTest.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementReorderTest {
    private fun e(pat: String, pos: Int) = ReplacementRuleEntity(
        scope = "global", bookId = null, pattern = pat, replacement = "x",
        isRegex = false, enabled = true, position = pos,
    )
    @Test fun `move down reindexes positions contiguously`() {
        val list = listOf(e("a", 0), e("b", 1), e("c", 2))
        val out = ReplacementReorder.reassignPositions(list, from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), out.map { it.pattern })
        assertEquals(listOf(0, 1, 2), out.map { it.position })
    }
    @Test fun `move up reindexes positions`() {
        val list = listOf(e("a", 0), e("b", 1), e("c", 2))
        val out = ReplacementReorder.reassignPositions(list, from = 2, to = 0)
        assertEquals(listOf("c", "a", "b"), out.map { it.pattern })
        assertEquals(listOf(0, 1, 2), out.map { it.position })
    }
    @Test fun `no-op or out-of-range still normalizes positions`() {
        val list = listOf(e("a", 5), e("b", 9))
        val out = ReplacementReorder.reassignPositions(list, from = 0, to = 0)
        assertEquals(listOf("a", "b"), out.map { it.pattern })
        assertEquals(listOf(0, 1), out.map { it.position })
    }
}
```

`RuleMapperTest.kt`:
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleMapperTest {
    @Test fun `toEntity maps order to position and carries scope`() {
        val domain = ReplacementRule(pattern = "p", replacement = "r", isRegex = true, enabled = false, order = 4)
        val entity = RuleMapper.toEntity(domain, scope = "book", bookId = "b1", id = 7)
        assertEquals(7L, entity.id)
        assertEquals("book", entity.scope)
        assertEquals("b1", entity.bookId)
        assertEquals(4, entity.position)
        assertEquals(true, entity.isRegex)
        assertEquals(false, entity.enabled)
    }
    @Test fun `toDomain then toEntity round-trips the rule fields`() {
        val entity = ReplacementRuleEntity(id = 1, scope = "global", bookId = null,
            pattern = "p", replacement = "r", isRegex = false, enabled = true, position = 3)
        val back = RuleMapper.toEntity(RuleMapper.toDomain(entity), scope = "global", bookId = null, id = 1)
        assertEquals(entity, back)
    }
}
```

- [ ] **Step 2: Run the tests — verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.replacement.*" --tests "com.nextz.kanomtts.data.repository.RuleMapperTest"`
Expected: FAIL — unresolved `RuleError`/`ReplacementRuleValidation`/`ReplacementReorder`/`RuleMapper.toEntity`.

- [ ] **Step 3: Implement**

`ReplacementRuleValidation.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

/** Why a rule is invalid; null from [ReplacementRuleValidation.validate] means OK. */
enum class RuleError { EmptyPattern, InvalidRegex }

object ReplacementRuleValidation {
    /**
     * A non-null result blocks saving. Regex patterns are validated by compiling them, because
     * ReplacementEngine compiles every enabled regex in its constructor (at the next playback
     * stream start) — a bad regex saved here would crash playback.
     */
    fun validate(pattern: String, isRegex: Boolean): RuleError? {
        if (pattern.isBlank()) return RuleError.EmptyPattern
        if (isRegex && runCatching { Regex(pattern) }.isFailure) return RuleError.InvalidRegex
        return null
    }
}
```

`ReplacementReorder.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

import com.nextz.kanomtts.data.db.ReplacementRuleEntity

object ReplacementReorder {
    /** Move item [from]→[to] and rewrite every position to its new index. Returns the reordered list. */
    fun reassignPositions(rules: List<ReplacementRuleEntity>, from: Int, to: Int): List<ReplacementRuleEntity> {
        val ordered = if (from in rules.indices && to in rules.indices && from != to) {
            rules.toMutableList().apply { add(to, removeAt(from)) }
        } else {
            rules
        }
        return ordered.mapIndexed { i, r -> if (r.position == i) r else r.copy(position = i) }
    }
}
```

In `RuleMapper.kt`, add `toEntity` next to `toDomain`:
```kotlin
    fun toEntity(rule: ReplacementRule, scope: String, bookId: String?, id: Long = 0): ReplacementRuleEntity =
        ReplacementRuleEntity(
            id = id,
            scope = scope,
            bookId = bookId,
            pattern = rule.pattern,
            replacement = rule.replacement,
            isRegex = rule.isRegex,
            enabled = rule.enabled,
            position = rule.order,
        )
```

- [ ] **Step 4: Run the tests — verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.replacement.*" --tests "com.nextz.kanomtts.data.repository.RuleMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleValidation.kt \
        app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementReorder.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/repository/RuleMapper.kt \
        app/src/test/kotlin/com/nextz/kanomtts/feature/replacement/ \
        app/src/test/kotlin/com/nextz/kanomtts/data/repository/RuleMapperTest.kt
git commit -m "feat(replacement): validation + reorder + RuleMapper.toEntity helpers"
```

---

## Task 2: DAO + repository additions (TDD, Robolectric Room)

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDao.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/repository/ReplacementRuleRepository.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDaoExtraTest.kt`

**Interfaces:**
- Produces: `ReplacementRuleDao.observeByBook(bookId): Flow<List<ReplacementRuleEntity>>`, `globalCount(): Flow<Int>`, `suspend updateAll(rules: List<ReplacementRuleEntity>)`; `ReplacementRuleRepository.observeByBook(bookId): Flow<List<ReplacementRuleEntity>>`, `globalCount(): Flow<Int>`, `suspend reorder(rules: List<ReplacementRuleEntity>)`.

- [ ] **Step 1: Write the failing test**

`ReplacementRuleDaoExtraTest.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementRuleDaoExtraTest : RoomTestBase() {
    private fun rule(scope: String, bookId: String?, pattern: String, pos: Int) =
        ReplacementRuleEntity(scope = scope, bookId = bookId, pattern = pattern,
            replacement = "x", isRegex = false, enabled = true, position = pos)

    @Test fun `observeByBook returns only that book's rules, ordered`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(rule("global", null, "g", 0))
        dao.insert(rule("book", "b1", "second", 2))
        dao.insert(rule("book", "b1", "first", 1))
        dao.insert(rule("book", "b2", "other", 0))
        assertEquals(listOf("first", "second"), dao.observeByBook("b1").first().map { it.pattern })
    }
    @Test fun `globalCount counts only global rules`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(rule("global", null, "g1", 0))
        dao.insert(rule("global", null, "g2", 1))
        dao.insert(rule("book", "b1", "b", 0))
        assertEquals(2, dao.globalCount().first())
    }
    @Test fun `updateAll persists new positions`() = runTest {
        val dao = db.replacementRuleDao()
        val id1 = dao.insert(rule("global", null, "a", 0))
        val id2 = dao.insert(rule("global", null, "b", 1))
        dao.updateAll(listOf(
            rule("global", null, "a", 1).copy(id = id1),
            rule("global", null, "b", 0).copy(id = id2),
        ))
        assertEquals(listOf("b", "a"), dao.observeGlobal().first().map { it.pattern }) // reordered by position
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.data.db.ReplacementRuleDaoExtraTest"`
Expected: FAIL — `observeByBook`/`globalCount`/`updateAll` unresolved.

- [ ] **Step 3: Implement**

In `ReplacementRuleDao.kt`, add inside the interface (imports `androidx.room.Update` is already present for `@Update`):
```kotlin
    @Query("SELECT * FROM replacement_rules WHERE scope = 'book' AND bookId = :bookId ORDER BY position ASC")
    fun observeByBook(bookId: String): Flow<List<ReplacementRuleEntity>>

    @Query("SELECT COUNT(*) FROM replacement_rules WHERE scope = 'global'")
    fun globalCount(): Flow<Int>

    @Update
    suspend fun updateAll(rules: List<ReplacementRuleEntity>)
```

In `ReplacementRuleRepository.kt`, add methods:
```kotlin
    fun observeByBook(bookId: String): Flow<List<ReplacementRuleEntity>> = dao.observeByBook(bookId)
    fun globalCount(): Flow<Int> = dao.globalCount()
    suspend fun reorder(rules: List<ReplacementRuleEntity>) = dao.updateAll(rules)
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.data.db.ReplacementRuleDaoExtraTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDao.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/repository/ReplacementRuleRepository.kt \
        app/src/test/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDaoExtraTest.kt
git commit -m "feat(replacement): DAO/repo observeByBook + globalCount + reorder(updateAll)"
```

---

## Task 3: ReplacementEditorViewModel (TDD, Robolectric + in-memory repo)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorViewModel.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `ReplacementRuleRepository` (Task 2 methods + existing `observeGlobal`/`add`/`update`/`remove`), `ReplacementRuleValidation`/`ReplacementReorder` (Task 1), `RuleMapper.toDomain/toEntity`, `ReplacementTsv` (`text.replacement`).
- Produces: `ReplacementEditorUiState(rules: List<ReplacementRuleEntity>, inheritedGlobalCount: Int, isBookScope: Boolean)`; `ReplacementEditorViewModel` with `uiState`, `addRule(pattern, replacement, isRegex)`, `updateRule(entity)`, `deleteRule(id)`, `toggleEnabled(entity)`, `reorder(from, to)`, `exportTsv(): String`, `importTsv(text)`. The VM reads `bookId: String?` from `SavedStateHandle["bookId"]` (null = global scope).

- [ ] **Step 1: Write the failing test**

`ReplacementEditorViewModelTest.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

import androidx.lifecycle.SavedStateHandle
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import com.nextz.kanomtts.data.util.RoomTestBase
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

class ReplacementEditorViewModelTest : RoomTestBase() {
    private lateinit var repo: ReplacementRuleRepository

    @Before fun setUpVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = ReplacementRuleRepository(db.replacementRuleDao())
    }
    @After fun tearDownVm() { Dispatchers.resetMain() }

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
    @Test fun `importTsv adds rules and exportTsv round-trips`() = runTest {
        val vm = globalVm()
        val tsv = "pattern\treplacement\tisRegex\tenabled\torder\nกข\tคง\tfalse\ttrue\t0"
        vm.importTsv(tsv)
        vm.uiState.first { it.rules.isNotEmpty() }
        assertTrue(vm.exportTsv().contains("กข\tคง"))
    }
}
```

- [ ] **Step 2: Run the test — verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.replacement.ReplacementEditorViewModelTest"`
Expected: FAIL — `ReplacementEditorViewModel` unresolved.

- [ ] **Step 3: Implement**

`ReplacementEditorViewModel.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import com.nextz.kanomtts.data.repository.RuleMapper
import com.nextz.kanomtts.text.replacement.ReplacementTsv
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReplacementEditorUiState(
    val rules: List<ReplacementRuleEntity> = emptyList(),
    val inheritedGlobalCount: Int = 0,
    val isBookScope: Boolean = false,
)

@HiltViewModel
class ReplacementEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ReplacementRuleRepository,
) : ViewModel() {

    private val bookId: String? = savedStateHandle["bookId"]
    private val scopeStr: String = if (bookId == null) "global" else "book"

    val uiState: StateFlow<ReplacementEditorUiState> =
        if (bookId == null) {
            repo.observeGlobal().map { ReplacementEditorUiState(it, 0, false) }
        } else {
            combine(repo.observeByBook(bookId), repo.globalCount()) { rules, gc ->
                ReplacementEditorUiState(rules, gc, true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplacementEditorUiState(isBookScope = bookId != null))

    fun addRule(pattern: String, replacement: String, isRegex: Boolean) {
        if (ReplacementRuleValidation.validate(pattern, isRegex) != null) return
        viewModelScope.launch {
            repo.add(ReplacementRuleEntity(
                scope = scopeStr, bookId = bookId, pattern = pattern, replacement = replacement,
                isRegex = isRegex, enabled = true, position = -1, // -1 → repo auto-appends
            ))
        }
    }

    fun updateRule(rule: ReplacementRuleEntity) {
        if (ReplacementRuleValidation.validate(rule.pattern, rule.isRegex) != null) return
        viewModelScope.launch { repo.update(rule) }
    }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.remove(id) }

    fun toggleEnabled(rule: ReplacementRuleEntity) =
        viewModelScope.launch { repo.update(rule.copy(enabled = !rule.enabled)) }

    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        repo.reorder(ReplacementReorder.reassignPositions(uiState.value.rules, from, to))
    }

    fun exportTsv(): String = ReplacementTsv.export(uiState.value.rules.map(RuleMapper::toDomain))

    fun importTsv(text: String) = viewModelScope.launch {
        ReplacementTsv.import(text).forEach { domain ->
            repo.add(RuleMapper.toEntity(domain, scopeStr, bookId).copy(position = -1))
        }
    }
}
```

- [ ] **Step 4: Run the test — verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.nextz.kanomtts.feature.replacement.ReplacementEditorViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorViewModel.kt \
        app/src/test/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorViewModelTest.kt
git commit -m "feat(replacement): ReplacementEditorViewModel (global/book scope, CRUD, reorder, TSV)"
```

---

## Task 4: Editor screen + add/edit sheet + routes + Settings row (integration)

Builds the editor UI **without** drag-reorder (Task 5 adds drag) — for now rows show position order with no reordering gesture. Verified by `:app:assembleDebug` + the full suite staying green; the screens themselves are device-validated.

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleSheet.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ReplacementEditorViewModel` (Task 3), `ReplacementRuleValidation`/`RuleError` (Task 1), `ReplacementRuleEntity`.
- Produces: `ReplacementEditorScreen(modifier, contentPadding, onBack, viewModel = hiltViewModel())`; `ReplacementRuleSheet(initial, onSave, onDismiss)`; `SettingsScreen` gains `onOpenReplacements: () -> Unit`; routes `replacements` and `replacements/{bookId}`.

- [ ] **Step 1: Add the strings**

In `res/values/strings.xml` add:
```xml
<string name="settings_replacements">คำแทนที่</string>
<string name="replacements_title">คำแทนที่</string>
<string name="replacements_empty">ยังไม่มีกฎ — เพิ่มกฎแรก</string>
<string name="replacements_inherited">+ %1$d กฎทั่วไปก็มีผลด้วย</string>
<string name="rule_pattern_hint">คำ/แพทเทิร์น</string>
<string name="rule_replacement_hint">แทนที่ด้วย</string>
<string name="rule_regex">ใช้ Regex</string>
<string name="rule_error_empty">ต้องไม่ว่าง</string>
<string name="rule_error_regex">Regex ไม่ถูกต้อง</string>
<string name="action_save">บันทึก</string>
<string name="action_delete">ลบ</string>
<string name="action_add">เพิ่ม</string>
```

- [ ] **Step 2: Implement the add/edit sheet**

`ReplacementRuleSheet.kt`:
```kotlin
package com.nextz.kanomtts.feature.replacement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.db.ReplacementRuleEntity

/** Bottom sheet for adding or editing one rule. [initial] null = add; non-null = edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplacementRuleSheet(
    initial: ReplacementRuleEntity?,
    prefilledPattern: String = "",
    onSave: (pattern: String, replacement: String, isRegex: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf(initial?.pattern ?: prefilledPattern) }
    var replacement by remember { mutableStateOf(initial?.replacement ?: "") }
    var isRegex by remember { mutableStateOf(initial?.isRegex ?: false) }
    val error = ReplacementRuleValidation.validate(pattern, isRegex)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = pattern, onValueChange = { pattern = it },
                label = { Text(stringResource(R.string.rule_pattern_hint)) },
                isError = error == RuleError.EmptyPattern || error == RuleError.InvalidRegex,
                supportingText = {
                    when (error) {
                        RuleError.EmptyPattern -> Text(stringResource(R.string.rule_error_empty))
                        RuleError.InvalidRegex -> Text(stringResource(R.string.rule_error_regex))
                        null -> {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = replacement, onValueChange = { replacement = it },
                label = { Text(stringResource(R.string.rule_replacement_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rule_regex), Modifier.weight(1f))
                Switch(checked = isRegex, onCheckedChange = { isRegex = it })
            }
            Button(
                onClick = { onSave(pattern.trim(), replacement, isRegex); onDismiss() },
                enabled = error == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
```
(Add the missing import `androidx.compose.ui.Alignment` if the compiler flags it.)

- [ ] **Step 3: Implement the editor screen**

`ReplacementEditorScreen.kt` (no drag yet — Task 5 adds it):
```kotlin
package com.nextz.kanomtts.feature.replacement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.db.ReplacementRuleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplacementEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ReplacementEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<ReplacementRuleEntity?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replacements_title)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addOpen = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        },
    ) { inner ->
        Column(Modifier.padding(inner).padding(contentPadding)) {
            if (state.isBookScope && state.inheritedGlobalCount > 0) {
                Text(
                    stringResource(R.string.replacements_inherited, state.inheritedGlobalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.rules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.replacements_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rules, key = { it.id }) { rule ->
                        RuleRow(
                            rule = rule,
                            onToggle = { viewModel.toggleEnabled(rule) },
                            onEdit = { editing = rule },
                            onDelete = { viewModel.deleteRule(rule.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (addOpen) {
        ReplacementRuleSheet(
            initial = null,
            onSave = { p, r, rx -> viewModel.addRule(p, r, rx) },
            onDismiss = { addOpen = false },
        )
    }
    editing?.let { rule ->
        ReplacementRuleSheet(
            initial = rule,
            onSave = { p, r, rx -> viewModel.updateRule(rule.copy(pattern = p, replacement = r, isRegex = rx)) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RuleRow(
    rule: ReplacementRuleEntity,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${rule.pattern} → ${rule.replacement}", style = MaterialTheme.typography.bodyMedium)
            if (rule.isRegex) Text("regex", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
        IconButton(onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
    }
}
```
(Add `import androidx.compose.foundation.clickable` for the row.)

- [ ] **Step 4: Wire the route + Settings row**

In `KanomApp.kt` NavHost, add two routes (the book route takes the `bookId` arg, surfaced to the VM via `SavedStateHandle["bookId"]`):
```kotlin
            composable("replacements") {
                ReplacementEditorScreen(onBack = { navController.popBackStack() }, contentPadding = padding)
            }
            composable("replacements/{bookId}") {
                ReplacementEditorScreen(onBack = { navController.popBackStack() }, contentPadding = padding)
            }
```
And pass a navigation lambda to `SettingsScreen`:
```kotlin
            composable(KanomDestination.SETTINGS.route) {
                SettingsScreen(Modifier, padding, onOpenReplacements = { navController.navigate("replacements") })
            }
```
In `SettingsScreen.kt`, add the param + a row (after the Playback section, before the premium stub):
```kotlin
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenReplacements: () -> Unit = {},
    playbackVm: PlaybackSettingsViewModel = hiltViewModel(),
) {
    // ... existing content ...
    HorizontalDivider(Modifier.padding(vertical = 16.dp))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpenReplacements).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.settings_replacements), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
```
(Add imports: `androidx.compose.foundation.clickable`, `androidx.compose.ui.res.stringResource`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight`, `com.nextz.kanomtts.R`.)

- [ ] **Step 5: Build + suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; suite green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt \
        app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementRuleSheet.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/screens/SettingsScreen.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(replacement): editor screen + add/edit sheet + Settings row + routes"
```

---

## Task 5: Drag-reorder in the editor list (integration; device-iteration-likely)

Hand-rolled long-press drag for the `LazyColumn` (no reorderable lib on this toolchain). On drop, persist via `viewModel.reorder(from, to)`. This is the task most likely to need device tuning — keep it isolated.

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt`

**Interfaces:**
- Consumes: `ReplacementEditorViewModel.reorder(from, to)`.

- [ ] **Step 1: Add a reorder state + gesture**

Add this drag-state holder + the wired list. Replace the `LazyColumn(...) { items(...) }` block from Task 4 with:
```kotlin
val listState = rememberLazyListState()
var draggingId by remember { mutableStateOf<Long?>(null) }
var dragOffset by remember { mutableFloatStateOf(0f) }
// Local working copy so the row follows the finger; committed to the VM on drop.
var working by remember(state.rules) { mutableStateOf(state.rules) }

LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
    itemsIndexed(working, key = { _, r -> r.id }) { index, rule ->
        val dragging = draggingId == rule.id
        RuleRow(
            rule = rule,
            dragging = dragging,
            onToggle = { viewModel.toggleEnabled(rule) },
            onEdit = { editing = rule },
            onDelete = { viewModel.deleteRule(rule.id) },
            dragHandleModifier = Modifier.pointerInput(rule.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingId = rule.id; dragOffset = 0f },
                    onDragEnd = {
                        val from = state.rules.indexOfFirst { it.id == rule.id }
                        val to = working.indexOfFirst { it.id == rule.id }
                        if (from >= 0 && to >= 0 && from != to) viewModel.reorder(from, to)
                        draggingId = null; dragOffset = 0f
                    },
                    onDragCancel = { draggingId = null; dragOffset = 0f; working = state.rules },
                    onDrag = { change, drag ->
                        change.consume()
                        dragOffset += drag.y
                        val curIndex = working.indexOfFirst { it.id == rule.id }
                        val rowPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return@detectDragGesturesAfterLongPress
                        // Swap with neighbor once the finger crosses half a row.
                        if (dragOffset > rowPx / 2 && curIndex < working.lastIndex) {
                            working = working.toMutableList().apply { add(curIndex + 1, removeAt(curIndex)) }
                            dragOffset -= rowPx
                        } else if (dragOffset < -rowPx / 2 && curIndex > 0) {
                            working = working.toMutableList().apply { add(curIndex - 1, removeAt(curIndex)) }
                            dragOffset += rowPx
                        }
                    },
                )
            },
        )
        HorizontalDivider()
    }
}
```
Update `RuleRow` to accept `dragging: Boolean` and `dragHandleModifier: Modifier`, and render a drag-handle icon carrying that modifier (e.g. `Icons.Filled.DragHandle`); optionally tint the row when `dragging`. Imports to add: `androidx.compose.foundation.lazy.rememberLazyListState`, `androidx.compose.foundation.lazy.itemsIndexed`, `androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress`, `androidx.compose.ui.input.pointer.pointerInput`, `androidx.compose.runtime.mutableFloatStateOf`, `androidx.compose.material.icons.filled.DragHandle`.

- [ ] **Step 2: Build + suite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; suite green. (Drag behavior itself is device-validated — the `reorder` persistence is already unit-tested in Task 3.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt
git commit -m "feat(replacement): long-press drag-reorder in the rule editor"
```

---

## Task 6: TSV import/export via SAF (integration)

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ReplacementEditorViewModel.exportTsv()` / `importTsv(text)`; `LocalContext`.

- [ ] **Step 1: Add strings**

```xml
<string name="rules_import">นำเข้า TSV</string>
<string name="rules_export">ส่งออก TSV</string>
<string name="rules_imported">นำเข้ากฎแล้ว</string>
<string name="rules_exported">ส่งออกแล้ว</string>
```

- [ ] **Step 2: Add SAF launchers + top-bar actions**

In `ReplacementEditorScreen`, mirror the `LibraryScreen` SAF pattern. Read TSV from the picked Uri via `contentResolver.openInputStream`; write export via `openOutputStream`. Add to the screen body:
```kotlin
val context = LocalContext.current
val scope = rememberCoroutineScope()
val snackbar = remember { SnackbarHostState() }

val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (text != null) viewModel.importTsv(text)
    }
}
val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri ->
    if (uri != null) {
        val tsv = viewModel.exportTsv()
        scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(tsv) }
        }
    }
}
```
Add `actions` to the `TopAppBar`:
```kotlin
            actions = {
                IconButton(onClick = { importLauncher.launch(arrayOf("text/tab-separated-values", "text/plain", "text/*")) }) {
                    Icon(Icons.Filled.FileDownload, stringResource(R.string.rules_import))
                }
                IconButton(onClick = { exportLauncher.launch("replacements.tsv") }) {
                    Icon(Icons.Filled.FileUpload, stringResource(R.string.rules_export))
                }
            },
```
Add `Scaffold(snackbarHost = { SnackbarHost(snackbar) }, ...)`. Imports: `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.compose.ui.platform.LocalContext`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `androidx.compose.material.icons.filled.FileDownload`, `androidx.compose.material.icons.filled.FileUpload`. (No persistable-permission needed — these are one-shot read/write, unlike the EPUB library which keeps the URI.)

- [ ] **Step 3: Build + suite + lint**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; suite green; no new lint errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ReplacementEditorScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(replacement): TSV import/export via SAF in the rule editor"
```

---

## Task 7: Reader Add-Replacement quick-add + manage-replacements row (integration)

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `ReplacementRuleRepository.add`, `RuleMapper.toEntity`, `ReplacementRuleSheet` (Task 4), `ReplacementRuleValidation`.
- Produces: `ReaderViewModel.quickAddText: StateFlow<String?>`, `dismissQuickAdd()`, `addReplacement(pattern, replacement, isRegex, toThisBook: Boolean)`; `ReaderScreen` gains `onOpenReplacements: (bookId: String) -> Unit`.

- [ ] **Step 1: Inject the repo + add the quick-add state/actions to `ReaderViewModel`**

Add `private val replacementRuleRepository: ReplacementRuleRepository,` to the constructor (after `bookmarkStore`). Add:
```kotlin
    private val _quickAddText = MutableStateFlow<String?>(null)
    val quickAddText: StateFlow<String?> = _quickAddText.asStateFlow()
    fun dismissQuickAdd() { _quickAddText.value = null }

    /** Save a replacement rule from the selection. [toThisBook] false = global (default), true = this book. */
    fun addReplacement(pattern: String, replacement: String, isRegex: Boolean, toThisBook: Boolean) {
        if (com.nextz.kanomtts.feature.replacement.ReplacementRuleValidation.validate(pattern, isRegex) != null) return
        viewModelScope.launch {
            replacementRuleRepository.add(
                com.nextz.kanomtts.data.db.ReplacementRuleEntity(
                    scope = if (toThisBook) "book" else "global",
                    bookId = if (toThisBook) bookId else null,
                    pattern = pattern, replacement = replacement, isRegex = isRegex,
                    enabled = true, position = -1,
                )
            )
            _quickAddText.value = null
            toast(R.string.toast_replacement_added)
        }
    }
```
Replace the `R.id.add_replacement` arm of the `when` (it currently shares the coming-soon toast with `translate_selection`) so it reads the selection and opens the quick-add. Change:
```kotlin
            R.id.translate_selection, R.id.add_replacement -> {
                toast(R.string.toast_coming_soon)
                mode.finish()
            }
```
to:
```kotlin
            R.id.translate_selection -> {
                toast(R.string.toast_coming_soon)
                mode.finish()
            }
            R.id.add_replacement -> viewModelScope.launch {
                val nav = navigator as? SelectableNavigator
                val text = stripZwsp(nav?.currentSelection()?.locator?.text?.highlight.orEmpty()).trim()
                nav?.clearSelection()
                mode.finish()
                if (text.isNotEmpty()) _quickAddText.value = text
            }
```
Add imports if needed: `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.asStateFlow` (likely already present).

- [ ] **Step 2: Add strings**

```xml
<string name="toast_replacement_added">เพิ่มคำแทนที่แล้ว</string>
<string name="reader_manage_replacements">จัดการคำแทนที่</string>
<string name="rule_scope_global">ทั่วไป</string>
<string name="rule_scope_book">เฉพาะเล่มนี้</string>
```

- [ ] **Step 3: Quick-add sheet + manage row in `ReaderScreen`**

In `ReaderScreen` (where the other sheets are conditionally shown), collect `quickAddText` and show a quick-add sheet reusing `ReplacementRuleSheet` plus a scope choice (default Global). Add:
```kotlin
val quickAdd by viewModel.quickAddText.collectAsState()
quickAdd?.let { selected ->
    var toThisBook by remember { mutableStateOf(false) } // default Global
    // Reuse the rule sheet; add a scope FilterChip row above it (or wrap). Minimal version:
    ReplacementRuleSheet(
        initial = null,
        prefilledPattern = selected,
        onSave = { p, r, rx -> viewModel.addReplacement(p, r, rx, toThisBook) },
        onDismiss = { viewModel.dismissQuickAdd() },
    )
}
```
For the scope choice, extend `ReplacementRuleSheet` with an optional `scopeChoice: (@Composable () -> Unit)? = null` slot rendered above the Save button, or add a dedicated `QuickAddReplacementSheet` that wraps the same fields plus two `FilterChip`s (ทั่วไป default-selected / เฉพาะเล่มนี้) bound to `toThisBook`. Prefer extending `ReplacementRuleSheet` with the optional slot to stay DRY.

Add the manage row to `ReaderSettingsSheet`: thread an `onOpenReplacements: () -> Unit` callback from `ReaderScreen` (which gets it as a param) into `ReaderSettingsSheet`, and render a clickable row:
```kotlin
Row(Modifier.fillMaxWidth().clickable { onDismiss(); onOpenReplacements() }.padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Text(stringResource(R.string.reader_manage_replacements), Modifier.weight(1f))
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
}
```
`ReaderScreen` gains `onOpenReplacements: (bookId: String) -> Unit`; the settings-sheet row calls `onOpenReplacements(bookId)`.

- [ ] **Step 4: Wire navigation in `KanomApp`**

Update the reader composable to pass the callback:
```kotlin
            composable("reader/{bookId}") { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                val vm: ReaderViewModel = hiltViewModel()
                ReaderScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                    onOpenReplacements = { id -> navController.navigate("replacements/$id") },
                    viewModel = vm,
                    contentPadding = padding,
                )
            }
```

- [ ] **Step 5: Build + suite + lint**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; suite green; no new lint errors.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt \
        app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderScreen.kt \
        app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderSettingsSheet.kt \
        app/src/main/kotlin/com/nextz/kanomtts/ui/navigation/KanomApp.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(replacement): reader Add-Replacement quick-add + manage-replacements row"
```

---

## Final integration pass (after Task 7)

- [ ] Full suite: `./gradlew :text:pipeline:test :text:replacement:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — all green.
- [ ] Opus whole-branch review; record Minor findings in `.superpowers/sdd/progress.md`.
- [ ] Device matrix: add a global rule → word pronounced differently on next playback start; Add-Replacement from a selection prefilled + scope choice (default Global) → saved; per-book rule only affects that book; reader "จัดการคำแทนที่" opens the book editor; drag-reorder persists; TSV export then import round-trips; invalid regex rejected with inline error (no crash).
- [ ] `finishing-a-development-branch` to merge.

---

## Self-Review (completed during authoring)

**Spec coverage:** editor (Tasks 3–5) ↔ §3.2; per-book reader-side (Task 7) ↔ §3.1/§3.4; DAO/repo/mapper additions (Tasks 1–2) ↔ §3.3; TSV SAF (Task 6) ↔ §3.7; regex validation (Tasks 1,3,4,7) ↔ §3.5; reorder (Tasks 1,5) ↔ §3.6; apply-on-next-start ↔ unchanged `DefaultTtsContentSource`; out-of-scope (visual/pack/live-apply) excluded.

**Type consistency:** `RuleError`/`ReplacementRuleValidation.validate`/`ReplacementReorder.reassignPositions`/`RuleMapper.toEntity`/`ReplacementEditorUiState`/`ReplacementEditorViewModel` methods/`ReplacementRuleSheet`/`observeByBook`/`globalCount`/`reorder`/`quickAddText`/`addReplacement`/`onOpenReplacements` are used identically across producing and consuming tasks.

**Implementer must confirm on first open (noted inline):** the exact `RuleMapper` object/file shape; whether `ReaderViewModel` already imports the flow helpers; the `ReaderSettingsSheetContent` insertion point for the manage row; the `position=-1 → auto-append` behavior in `ReplacementRuleRepository.add` (verified in recon).

**Reorder caveat:** `reorder` persists the whole scope via `updateAll` (rare action; acceptable for v1 even at thousands of rules). Drag-reorder is hand-rolled (no compatible lib on this toolchain) — Task 5 is the device-iteration-likely task; if it proves fiddly on device, fall back to up/down move buttons (same `reorder(from,to)` call).
