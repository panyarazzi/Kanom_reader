# Plan 09b — Visual Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render `showInReader`-flagged replacement rules in the reader (a parse-time `openForDisplay` transform), keeping the TTS karaoke highlight aligned, without changing the spoken text.

**Architecture:** A per-rule `showInReader` flag (entity + domain + TSV) gates which rules render. When the `visualReplacement` reader pref is on, `ReaderViewModel.load()` snapshots the `showInReader` effective rules and passes them to `openForDisplay`, which runs a new `ReplacementInjector` (replace text nodes) **before** `ThaiZwspInjector`. The same engine re-derives the TTS highlight's quote so Readium's text-match still lands. Applies on next book open (consistent with how `thaiWordBreak` already works); spoken path is untouched.

**Tech Stack:** Kotlin, Room 2.8.4 (+ a v1→v2 migration), Jsoup, Compose/Material3, Hilt, Coroutines, the `:text:replacement` engine, JUnit4 + Robolectric.

## Global Constraints

- **Toolchain:** AGP 9.2.1 BUILT-IN Kotlin 2.2.10 (no Kotlin plugins on `:app`); Java 17; Room 2.8.4 via KSP; Jsoup (already a dep, used by `ThaiZwspInjector`).
- **Apply-on-next-open, NOT live reload.** Recon confirmed toggling `thaiWordBreak` does not re-open the publication today (the prefs collector only `submitPreferences`); the pub is opened once in `load()`. `visualReplacement` matches that: snapshot the flag + rules at `load()`; a mid-read toggle/edit takes effect when the book is re-opened. Do NOT attempt a live publication/fragment swap.
- **Spoken text unchanged.** `DefaultTtsContentSource` (the TTS path via `open()`) and `TextPipeline` are NOT modified — TTS still applies ALL effective rules. Only the *display* and the *highlight quote* use the `showInReader` subset.
- **Composition order: replacement → then ZWSP.** In `openForDisplay`'s transformer, run `ReplacementInjector` first, then `ThaiZwspInjector`, so word-breaks are computed on the replaced text.
- **`showInReader` default false; `visualReplacement` pref default false.** New rules + existing (migrated) rules + the master toggle all default OFF — no behaviour change for users who don't opt in.
- **Regex still validated before save** (09a `ReplacementRuleValidation`) — unchanged; the display engine compiles `Regex` too.
- **Room migration must preserve data** (not destructive) — users have rules from 09a. `MIGRATION_1_2` is an additive `ALTER … ADD COLUMN … DEFAULT 0`.
- **Highlight under replacement is best-effort** (Readium decoration matching is a black box) — if a transformed quote fails to match on device, the highlight is simply dropped for that sentence (no crash). This is the primary device-validation item.
- **Tests:** `app/src/test/kotlin/...`, JUnit4 + `org.junit.Assert.*`, backtick names. Room/Android tests use Robolectric `@Config(sdk=[35])`; VM tests build their own in-memory Room with a **directExecutor** + `Dispatchers.setMain(UnconfinedTestDispatcher())` (the 09a pattern). `:text:replacement` tests are pure JVM.
- **Per-task verification MUST include `:app:lintDebug`** (the 09a lesson — lint errors only surfaced at the final gate). Commands: `./gradlew :text:replacement:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`, `./gradlew :app:lintDebug`.

---

## File Structure

**Create:**
- `app/src/main/kotlin/com/nextz/kanomtts/data/db/KanomMigrations.kt` — `MIGRATION_1_2` (Task 2).
- `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ReplacementInjector.kt` — display text-node transform (Task 5).
- `app/src/main/kotlin/com/nextz/kanomtts/reader/VisualHighlight.kt` — pure `transformHighlightQuote` helper (Task 6).
- Tests under `app/src/test/kotlin/...` per task.

**Modify:**
- `text/replacement/.../ReplacementRule.kt` (+ `showInReader`), `ReplacementTsv.kt` (6th column) — Task 1.
- `data/repository/RuleMapper.kt` (map `showInReader`) — Task 1.
- `data/db/ReplacementRuleEntity.kt` (+ column), `KanomDatabase.kt` (version 2), `data/di/DataModule.kt` (`.addMigrations`), `data/db/ReplacementRuleDao.kt` (+ `observeEffectiveVisual`), `data/repository/ReplacementRuleRepository.kt` (+ `observeVisualEffective`) — Task 2.
- `feature/replacement/ReplacementRuleSheet.kt`, `ReplacementEditorViewModel.kt`, `ReplacementEditorScreen.kt` (RuleRow), `reader/ReaderScreen.kt` (QuickAddReplacementSheet), `reader/ReaderViewModel.kt` (`addReplacement`), `res/values/strings.xml` — Task 3.
- `data/prefs/KanomPreferences.kt`, `ReaderPreferences.kt`, `PreferencesRepository.kt`, `reader/ReaderSettingsSheet.kt`, `reader/ReaderViewModel.kt` (`onVisualReplacement`) — Task 4.
- `reader/engine/PublicationProvider.kt` (`openForDisplay` signature + transformer), `reader/ReaderBookSource.kt` (interface + impl), `reader/ReaderViewModel.kt` (`load()` snapshot) — Task 5.
- `reader/ReaderViewModel.kt` (highlight collector) — Task 6.

---

## Task 1: `showInReader` on the domain rule + TSV + mapper (TDD)

**Files:**
- Modify: `text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementRule.kt`
- Modify: `text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementTsv.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/repository/RuleMapper.kt`
- Test: `text/replacement/src/test/kotlin/com/nextz/kanomtts/text/replacement/ReplacementTsvShowInReaderTest.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/repository/RuleMapperTest.kt` (extend)

**Interfaces:**
- Produces: `ReplacementRule(..., val showInReader: Boolean = false)`; `ReplacementTsv` 6-column format (tolerant 5-col import); `RuleMapper.toDomain`/`toEntity` carry `showInReader`.

- [ ] **Step 1: Write the failing tests**

`ReplacementTsvShowInReaderTest.kt`:
```kotlin
package com.nextz.kanomtts.text.replacement

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementTsvShowInReaderTest {
    @Test fun `export then import round-trips showInReader`() {
        val rules = listOf(
            ReplacementRule("a", "b", isRegex = false, enabled = true, order = 0, showInReader = true),
            ReplacementRule("c", "d", isRegex = false, enabled = true, order = 1, showInReader = false),
        )
        assertEquals(rules, ReplacementTsv.import(ReplacementTsv.export(rules)))
    }
    @Test fun `header has the showInReader column`() {
        assertEquals("pattern\treplacement\tisRegex\tenabled\torder\tshowInReader", ReplacementTsv.export(emptyList()))
    }
    @Test fun `old 5-column rows import with showInReader false`() {
        val old = "pattern\treplacement\tisRegex\tenabled\torder\nก\tข\tfalse\ttrue\t0"
        val imported = ReplacementTsv.import(old)
        assertEquals(1, imported.size)
        assertEquals(false, imported.first().showInReader)
    }
}
```

Extend `RuleMapperTest.kt` with:
```kotlin
    @Test fun `toEntity and toDomain carry showInReader`() {
        val domain = com.nextz.kanomtts.text.replacement.ReplacementRule("p", "r", isRegex = false, enabled = true, order = 0, showInReader = true)
        val entity = RuleMapper.toEntity(domain, scope = "global", bookId = null)
        assertEquals(true, entity.showInReader)
        assertEquals(true, RuleMapper.toDomain(entity).showInReader)
    }
```
(This depends on the entity field from Task 2 — write this assertion now but expect it to compile-fail until Task 2 adds `ReplacementRuleEntity.showInReader`; if running Task 1 standalone, stub the entity field first or order Task 2 before this assertion. The implementer should add the `showInReader` param to `RuleMapper.toEntity`/`toDomain` referencing `ReplacementRuleEntity.showInReader`, which Task 2 adds — so do Task 1's RuleMapper change together with Task 2's entity change if the build requires it. See note below.)

> **Build-order note for the implementer:** `RuleMapper` references `ReplacementRuleEntity`, so the mapper change (Task 1) and the entity change (Task 2) must compile together. Implement the entity field (Task 2 Step "entity") and the mapper change in whichever order keeps the tree compiling — it is fine to do the one-line entity field addition as part of this task and the migration/DAO as Task 2. The reviewer is told the entity column may appear in either task's diff.

- [ ] **Step 2: Run the tests — verify they fail**

Run: `./gradlew :text:replacement:test --tests "*ReplacementTsvShowInReaderTest"`
Expected: FAIL — `showInReader` not a parameter of `ReplacementRule`.

- [ ] **Step 3: Implement**

`ReplacementRule.kt` — add the field (engine ignores it; it's a passthrough data field):
```kotlin
data class ReplacementRule(
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean = false,
    val enabled: Boolean = true,
    val order: Int = 0,
    val showInReader: Boolean = false,
)
```

`ReplacementTsv.kt` — 6-column export + tolerant import:
```kotlin
    private const val HEADER = "pattern\treplacement\tisRegex\tenabled\torder\tshowInReader"

    fun export(rules: List<ReplacementRule>): String =
        buildString {
            append(HEADER)
            for (r in rules) {
                append('\n')
                append(r.pattern).append('\t')
                append(r.replacement).append('\t')
                append(r.isRegex).append('\t')
                append(r.enabled).append('\t')
                append(r.order).append('\t')
                append(r.showInReader)
            }
        }

    fun import(tsv: String): List<ReplacementRule> {
        val lines = tsv.split('\n')
        if (lines.isEmpty()) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val c = line.split('\t')
            if (c.size != 5 && c.size != 6) return@mapNotNull null   // tolerate old 5-col
            val isRegex = c[2].toBooleanStrictOrNull() ?: return@mapNotNull null
            val enabled = c[3].toBooleanStrictOrNull() ?: return@mapNotNull null
            val order = c[4].toIntOrNull() ?: return@mapNotNull null
            val showInReader = if (c.size == 6) c[5].toBooleanStrictOrNull() ?: return@mapNotNull null else false
            ReplacementRule(c[0], c[1], isRegex, enabled, order, showInReader)
        }
    }
```

`RuleMapper.kt` — carry `showInReader` (the entity field is added in Task 2 / per the build-order note):
```kotlin
    fun toDomain(e: ReplacementRuleEntity): ReplacementRule =
        ReplacementRule(
            pattern = e.pattern, replacement = e.replacement, isRegex = e.isRegex,
            enabled = e.enabled, order = e.position, showInReader = e.showInReader,
        )

    fun toEntity(rule: ReplacementRule, scope: String, bookId: String?, id: Long = 0): ReplacementRuleEntity =
        ReplacementRuleEntity(
            id = id, scope = scope, bookId = bookId, pattern = rule.pattern,
            replacement = rule.replacement, isRegex = rule.isRegex, enabled = rule.enabled,
            position = rule.order, showInReader = rule.showInReader,
        )
```

- [ ] **Step 4: Run the tests — verify they pass**

Run: `./gradlew :text:replacement:test --tests "*ReplacementTsvShowInReaderTest"` then `./gradlew :app:testDebugUnitTest --tests "*RuleMapperTest"`
Expected: PASS (RuleMapperTest passes once Task 2's entity field exists).

- [ ] **Step 5: Confirm the engine still ignores the new field + commit**

Run: `./gradlew :text:replacement:test` (the existing `ReplacementEngineTest` must stay green — the engine constructor filters on `enabled`/`isRegex` only, never `showInReader`).
```bash
git add text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementRule.kt \
        text/replacement/src/main/kotlin/com/nextz/kanomtts/text/replacement/ReplacementTsv.kt \
        text/replacement/src/test/kotlin/com/nextz/kanomtts/text/replacement/ReplacementTsvShowInReaderTest.kt \
        app/src/main/kotlin/com/nextz/kanomtts/data/repository/RuleMapper.kt \
        app/src/test/kotlin/com/nextz/kanomtts/data/repository/RuleMapperTest.kt
git commit -m "feat(replacement): showInReader on domain rule + TSV column + mapper"
```

---

## Task 2: Entity column + Room v1→v2 migration + visual-effective query (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleEntity.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/KanomDatabase.kt`
- Create: `app/src/main/kotlin/com/nextz/kanomtts/data/db/KanomMigrations.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/di/DataModule.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleDao.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/data/repository/ReplacementRuleRepository.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/db/Migration1To2Test.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleVisualDaoTest.kt`

**Interfaces:**
- Produces: `ReplacementRuleEntity(..., val showInReader: Boolean)`; `MIGRATION_1_2`; `ReplacementRuleDao.observeEffectiveVisual(bookId): Flow<List<ReplacementRuleEntity>>`; `ReplacementRuleRepository.observeVisualEffective(bookId): Flow<List<ReplacementRule>>`.

- [ ] **Step 1: Write the failing tests**

`Migration1To2Test.kt` — verifies the ALTER directly (no exported-schema dependency), via a raw v1-shaped table:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {
    @Test fun `migration adds showInReader defaulting to 0 and preserves rows`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(null) // in-memory
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE replacement_rules (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scope TEXT NOT NULL,
                            bookId TEXT, pattern TEXT NOT NULL, replacement TEXT NOT NULL,
                            isRegex INTEGER NOT NULL, enabled INTEGER NOT NULL, position INTEGER NOT NULL)""")
                        db.execSQL("INSERT INTO replacement_rules (scope,bookId,pattern,replacement,isRegex,enabled,position) VALUES ('global',NULL,'a','b',0,1,0)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build(),
        )
        val db = helper.writableDatabase
        MIGRATION_1_2.migrate(db)
        db.query("SELECT pattern, showInReader FROM replacement_rules").use { c ->
            assertEquals(true, c.moveToFirst())
            assertEquals("a", c.getString(0))
            assertEquals(0, c.getInt(1)) // default 0 = false, row preserved
        }
        db.close()
    }
}
```

`ReplacementRuleVisualDaoTest.kt` (extends `RoomTestBase`):
```kotlin
package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementRuleVisualDaoTest : RoomTestBase() {
    private fun rule(scope: String, bookId: String?, pat: String, show: Boolean, pos: Int) =
        ReplacementRuleEntity(scope = scope, bookId = bookId, pattern = pat, replacement = "x",
            isRegex = false, enabled = true, position = pos, showInReader = show)

    @Test fun `observeEffectiveVisual returns only showInReader effective rules ordered`() = runTest {
        val dao = db.replacementRuleDao()
        dao.insert(rule("global", null, "g-show", true, 0))
        dao.insert(rule("global", null, "g-hide", false, 1))
        dao.insert(rule("book", "b1", "b-show", true, 2))
        dao.insert(rule("book", "b2", "other", true, 3))
        assertEquals(listOf("g-show", "b-show"), dao.observeEffectiveVisual("b1").first().map { it.pattern })
    }
}
```

- [ ] **Step 2: Run — verify fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*Migration1To2Test" --tests "*ReplacementRuleVisualDaoTest"`
Expected: FAIL — `showInReader` / `MIGRATION_1_2` / `observeEffectiveVisual` unresolved.

- [ ] **Step 3: Implement**

`ReplacementRuleEntity.kt` — add the column:
```kotlin
    val position: Int,
    val showInReader: Boolean = false,
```

`KanomDatabase.kt` — bump version:
```kotlin
    version = 2,
```
> If `:app:assembleDebug` errors that schema export is required (because `exportSchema = true` with no `room.schemaLocation`), set `exportSchema = false` on the `@Database` annotation — acceptable for this pre-release app (the migration is tested directly above, not via `MigrationTestHelper`). Note the change in your report.

`KanomMigrations.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE replacement_rules ADD COLUMN showInReader INTEGER NOT NULL DEFAULT 0")
    }
}
```

`DataModule.kt` — add the migration to the builder:
```kotlin
    fun provideDatabase(@ApplicationContext ctx: Context): KanomDatabase =
        Room.databaseBuilder(ctx, KanomDatabase::class.java, "kanom.db")
            .addMigrations(com.nextz.kanomtts.data.db.MIGRATION_1_2)
            .build()
```

`ReplacementRuleDao.kt` — add the visual-effective query:
```kotlin
    @Query("""SELECT * FROM replacement_rules
              WHERE showInReader = 1 AND (scope = 'global' OR (scope = 'book' AND bookId = :bookId))
              ORDER BY position ASC""")
    fun observeEffectiveVisual(bookId: String): Flow<List<ReplacementRuleEntity>>
```

`ReplacementRuleRepository.kt` — domain projection:
```kotlin
    fun observeVisualEffective(bookId: String): Flow<List<ReplacementRule>> =
        dao.observeEffectiveVisual(bookId).map { list -> list.map(RuleMapper::toDomain) }
```

- [ ] **Step 4: Run — verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*Migration1To2Test" --tests "*ReplacementRuleVisualDaoTest"`
Expected: PASS.

- [ ] **Step 5: Build + lint + commit**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/ app/src/test/kotlin/com/nextz/kanomtts/data/db/Migration1To2Test.kt app/src/test/kotlin/com/nextz/kanomtts/data/db/ReplacementRuleVisualDaoTest.kt
git commit -m "feat(replacement): entity showInReader + Room v1→v2 migration + observeVisualEffective"
```

---

## Task 3: `showInReader` toggle in the editor + Add-Replacement

**Files:**
- Modify: `feature/replacement/ReplacementRuleSheet.kt`, `ReplacementEditorViewModel.kt`, `ReplacementEditorScreen.kt`
- Modify: `reader/ReaderScreen.kt` (QuickAddReplacementSheet), `reader/ReaderViewModel.kt` (`addReplacement`)
- Modify: `res/values/strings.xml`

**Interfaces:**
- Produces: `ReplacementRuleSheet(..., onSave: (pattern, replacement, isRegex, showInReader) -> Unit, initial)` (now 4-arg onSave); `ReplacementEditorViewModel.addRule(pattern, replacement, isRegex, showInReader)`, `updateRule(rule)` (rule already carries `showInReader`); `ReaderViewModel.addReplacement(pattern, replacement, isRegex, toThisBook, showInReader)`.

- [ ] **Step 1: Add strings**

```xml
<string name="rule_show_in_reader">แสดงในหน้าอ่าน</string>
```

- [ ] **Step 2: Add the Switch to `ReplacementRuleSheet` (4-arg onSave)**

Change the sheet to carry `showInReader` (seeded from `initial`), render a Switch, and pass it to `onSave`:
```kotlin
fun ReplacementRuleSheet(
    initial: ReplacementRuleEntity?,
    prefilledPattern: String = "",
    onSave: (pattern: String, replacement: String, isRegex: Boolean, showInReader: Boolean) -> Unit,
    onDismiss: () -> Unit,
    scopeContent: (@Composable () -> Unit)? = null,
) {
    var pattern by remember { mutableStateOf(initial?.pattern ?: prefilledPattern) }
    var replacement by remember { mutableStateOf(initial?.replacement ?: "") }
    var isRegex by remember { mutableStateOf(initial?.isRegex ?: false) }
    var showInReader by remember { mutableStateOf(initial?.showInReader ?: false) }
    val error = ReplacementRuleValidation.validate(pattern, isRegex)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            // ... pattern + replacement OutlinedTextFields unchanged ...
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rule_regex), Modifier.weight(1f))
                Switch(checked = isRegex, onCheckedChange = { isRegex = it })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rule_show_in_reader), Modifier.weight(1f))
                Switch(checked = showInReader, onCheckedChange = { showInReader = it })
            }
            scopeContent?.invoke()
            Button(
                onClick = { onSave(pattern.trim(), replacement, isRegex, showInReader); onDismiss() },
                enabled = error == null, modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
```

- [ ] **Step 3: Update the editor VM + screen callers**

`ReplacementEditorViewModel.addRule` gains `showInReader`:
```kotlin
fun addRule(pattern: String, replacement: String, isRegex: Boolean, showInReader: Boolean) {
    if (ReplacementRuleValidation.validate(pattern, isRegex) != null) return
    viewModelScope.launch {
        repo.add(ReplacementRuleEntity(
            scope = scopeStr, bookId = bookId, pattern = pattern, replacement = replacement,
            isRegex = isRegex, enabled = true, position = -1, showInReader = showInReader,
        ))
    }
}
```
In `ReplacementEditorScreen.kt`, the add/edit sheet call sites pass the 4-arg lambda:
```kotlin
// add:
onSave = { p, r, rx, show -> viewModel.addRule(p, r, rx, show) },
// edit:
onSave = { p, r, rx, show -> viewModel.updateRule(rule.copy(pattern = p, replacement = r, isRegex = rx, showInReader = show)) },
```
In `RuleRow`, add a 👁 badge when `rule.showInReader`:
```kotlin
            Column(Modifier.weight(1f)) {
                Text("${rule.pattern} → ${rule.replacement}", style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (rule.isRegex) Text("regex ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (rule.showInReader) Text("👁", style = MaterialTheme.typography.labelSmall)
                }
            }
```

- [ ] **Step 4: Update the reader Add-Replacement (QuickAddReplacementSheet + addReplacement)**

`QuickAddReplacementSheet`'s `onSave` already passes through `ReplacementRuleSheet`; update it to the 5-arg form (the showInReader Switch now lives in `ReplacementRuleSheet`, so the quick-add gets it for free):
```kotlin
private fun QuickAddReplacementSheet(
    selected: String,
    onSave: (pattern: String, replacement: String, isRegex: Boolean, toThisBook: Boolean, showInReader: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var toThisBook by remember { mutableStateOf(false) }
    ReplacementRuleSheet(
        initial = null, prefilledPattern = selected,
        onSave = { p, r, rx, show -> onSave(p, r, rx, toThisBook, show) },
        onDismiss = onDismiss,
        scopeContent = { /* unchanged scope FilterChips */ },
    )
}
```
`ReaderViewModel.addReplacement` gains `showInReader`:
```kotlin
fun addReplacement(pattern: String, replacement: String, isRegex: Boolean, toThisBook: Boolean, showInReader: Boolean) {
    if (com.nextz.kanomtts.feature.replacement.ReplacementRuleValidation.validate(pattern, isRegex) != null) return
    viewModelScope.launch {
        replacementRuleRepository.add(
            com.nextz.kanomtts.data.db.ReplacementRuleEntity(
                scope = if (toThisBook) "book" else "global", bookId = if (toThisBook) bookId else null,
                pattern = pattern, replacement = replacement, isRegex = isRegex,
                enabled = true, position = -1, showInReader = showInReader,
            )
        )
        _quickAddText.value = null
        toast(R.string.toast_replacement_added)
    }
}
```
And the `quickAdd?.let { ... }` call site in `ReaderScreen` passes the 5-arg `onSave` to `viewModel.addReplacement(p, r, rx, toThisBook, show)`.

- [ ] **Step 5: Build + full suite + lint**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; green; lint clean. (The `ReplacementEditorViewModelTest` import test calls `vm.importTsv` and `addRule`; if any existing test calls the old 3-arg `addRule`/`onSave`, update those call sites to the new arity.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/nextz/kanomtts/feature/replacement/ app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderScreen.kt app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt app/src/main/res/values/strings.xml
git commit -m "feat(replacement): show-in-reader toggle in editor + Add-Replacement"
```

---

## Task 4: `visualReplacement` reader pref + settings toggle

**Files:**
- Modify: `data/prefs/KanomPreferences.kt`, `data/prefs/ReaderPreferences.kt`, `data/prefs/PreferencesRepository.kt`
- Modify: `reader/ReaderSettingsSheet.kt`, `reader/ReaderViewModel.kt`
- Test: `app/src/test/kotlin/com/nextz/kanomtts/data/prefs/VisualReplacementPrefTest.kt`

**Interfaces:**
- Produces: `KanomPreferences.visualReplacement: Boolean = false`; `ReaderPreferences.setVisualReplacement(Boolean)`; `ReaderViewModel.onVisualReplacement(Boolean)`.

- [ ] **Step 1: Write the failing test** (round-trips through the real `PreferencesRepository` via `FakeReaderPreferences` or the existing prefs test pattern)
```kotlin
package com.nextz.kanomtts.data.prefs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualReplacementPrefTest {
    @Test fun `setVisualReplacement writes through the interface`() = runTest {
        val prefs: ReaderPreferences = FakeReaderPreferences()
        assertEquals(false, prefs.preferences.first().visualReplacement) // default off
        prefs.setVisualReplacement(true)
        assertEquals(true, prefs.preferences.first().visualReplacement)
    }
}
```

- [ ] **Step 2: Run — verify fail.** `./gradlew :app:testDebugUnitTest --tests "*VisualReplacementPrefTest"` → FAIL.

- [ ] **Step 3: Implement** (mirror the existing prefs fields exactly)
- `KanomPreferences.kt`: add `val visualReplacement: Boolean = false,` after `pageAnimation`.
- `ReaderPreferences.kt`: add `suspend fun setVisualReplacement(v: Boolean)`.
- `PreferencesRepository.kt`: add key `val visualReplacement = booleanPreferencesKey("visual_replacement")`; map `visualReplacement = p[Keys.visualReplacement] ?: DEFAULTS.visualReplacement,`; setter `override suspend fun setVisualReplacement(v: Boolean): Unit { store.edit { it[Keys.visualReplacement] = v } }`.
- Update `FakeReaderPreferences` (both fakes under `app/src/test`) with `override suspend fun setVisualReplacement(v: Boolean) = update { it.copy(visualReplacement = v) }` (match the fake's existing idiom).
- `ReaderViewModel.kt`: `fun onVisualReplacement(v: Boolean) = viewModelScope.launch { prefs.setVisualReplacement(v) }`.
- `ReaderSettingsSheet.kt`: add `onVisualReplacement: (Boolean) -> Unit = {}` to `ReaderSettingsSheetContent`, pass `onVisualReplacement = { viewModel.onVisualReplacement(it) }` from `ReaderSettingsSheet`, and a `ToggleRow(label = "แทนที่ในหน้าอ่าน", checked = prefs.visualReplacement, onCheckedChange = onVisualReplacement)` next to the `thaiWordBreak` row.

- [ ] **Step 4: Run — verify pass + build + lint.** `./gradlew :app:testDebugUnitTest --tests "*VisualReplacementPrefTest"`; then `./gradlew :app:assembleDebug :app:lintDebug`.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/data/prefs/ app/src/main/kotlin/com/nextz/kanomtts/reader/ app/src/test/kotlin/com/nextz/kanomtts/
git commit -m "feat(reader): visualReplacement pref + settings toggle"
```

---

## Task 5: `ReplacementInjector` + `openForDisplay` threading (apply-on-next-open)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/ReplacementInjector.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/engine/PublicationProvider.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderBookSource.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt` (`load()`)
- Test: `app/src/test/kotlin/com/nextz/kanomtts/reader/engine/ReplacementInjectorTest.kt`

**Interfaces:**
- Consumes: `ReplacementEngine` (`:text:replacement`), `ReplacementRuleRepository.observeVisualEffective` (Task 2), `KanomPreferences.visualReplacement` (Task 4).
- Produces: `ReplacementInjector.inject(html: String, engine: ReplacementEngine): String`; `PublicationProvider.openForDisplay(uri, thaiWordBreak, visualRules: List<ReplacementRule>?)`; `ReaderBookSource.openForDisplay(uri, thaiWordBreak, visualRules)`.

- [ ] **Step 1: Write the failing test**
```kotlin
package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.text.replacement.ReplacementEngine
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementInjectorTest {
    private fun engine(vararg r: ReplacementRule) = ReplacementEngine(r.toList())

    @Test fun `replaces text node content`() {
        val out = ReplacementInjector.inject("<html><body><p>hello world</p></body></html>",
            engine(ReplacementRule("hello", "hi", showInReader = true)))
        assertTrue(out.contains("hi world"))
        assertTrue(!out.contains("hello world"))
    }
    @Test fun `skips script and style`() {
        val html = "<html><body><script>hello</script><style>hello</style><p>hello</p></body></html>"
        val out = ReplacementInjector.inject(html, engine(ReplacementRule("hello", "hi", showInReader = true)))
        assertTrue(out.contains("<script>hello</script>") || out.contains("hello</script>"))
        assertTrue(out.contains("hi</p>") || out.contains(">hi<"))
    }
    @Test fun `no-op engine leaves text unchanged`() {
        val html = "<html><body><p>keep me</p></body></html>"
        assertEquals(true, ReplacementInjector.inject(html, engine()).contains("keep me"))
    }
}
```

- [ ] **Step 2: Run — verify fail.** `./gradlew :app:testDebugUnitTest --tests "*ReplacementInjectorTest"` → FAIL.

- [ ] **Step 3: Implement `ReplacementInjector`** (mirror `ThaiZwspInjector`'s Jsoup traversal)
```kotlin
package com.nextz.kanomtts.reader.engine

import com.nextz.kanomtts.text.replacement.ReplacementEngine
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** Applies a ReplacementEngine to every visible text node of an XHTML document (skips script/style). */
object ReplacementInjector {
    fun inject(html: String, engine: ReplacementEngine): String {
        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                if (node !is TextNode) return
                val parent = (node.parent() as? Element)?.tagName()?.lowercase()
                if (parent == "script" || parent == "style") return
                val text = node.wholeText
                if (text.isBlank()) return
                val transformed = engine.apply(text)
                if (transformed != text) node.text(transformed)
            }
            override fun tail(node: Node, depth: Int) {}
        }, doc)
        return doc.outerHtml()
    }
}
```

- [ ] **Step 4: Thread through `openForDisplay`** (replacement → then ZWSP; null/empty rules = today's behaviour)
```kotlin
suspend fun openForDisplay(uri: String, thaiWordBreak: Boolean, visualRules: List<ReplacementRule>?): Publication? =
    withContext(Dispatchers.IO) {
        val engine = visualRules?.takeIf { it.isNotEmpty() }?.let { ReplacementEngine(it) }
        if (!thaiWordBreak && engine == null) return@withContext open(uri)

        val url = Uri.parse(uri).toAbsoluteUrl() ?: return@withContext null
        val asset = assetRetriever.retrieve(url).getOrElse { return@withContext null }
        if (asset !is ContainerAsset) { asset.close(); return@withContext open(uri) }

        val transformer: (Url, Resource) -> Resource = { entryUrl, res ->
            val s = entryUrl.toString()
            if (s.endsWith(".xhtml", ignoreCase = true) || s.endsWith(".html", ignoreCase = true)) {
                res.map { bytes: ByteArray ->
                    var html = bytes.decodeToString()
                    if (engine != null) html = ReplacementInjector.inject(html, engine)
                    if (thaiWordBreak) html = ThaiZwspInjector.inject(html)
                    Try.success(html.encodeToByteArray())
                }
            } else res
        }
        val wrappedAsset = ContainerAsset(format = asset.format, container = TransformingContainer(asset.container, transformer))
        opener.open(wrappedAsset, allowUserInteraction = false).getOrElse { wrappedAsset.close(); return@withContext null }
    }
```
Add the import `com.nextz.kanomtts.text.replacement.ReplacementEngine` + `ReplacementRule`. Update `ReaderBookSource.openForDisplay(uri, thaiWordBreak, visualRules)` (interface + `DefaultReaderBookSource` impl → `publications.openForDisplay(uri, thaiWordBreak, visualRules)`).

- [ ] **Step 5: Snapshot in `ReaderViewModel.load()`** (apply-on-next-open)
After reading `initialPrefs`, snapshot and pass the visual rules:
```kotlin
    val initialPrefs = prefs.preferences.first()
    val visualRules = if (initialPrefs.visualReplacement)
        replacementRuleRepository.observeVisualEffective(bookId).first() else emptyList()
    val publication = source.openForDisplay(book.uri, initialPrefs.thaiWordBreak, visualRules)
```
(Hold the engine for the highlight task: `visualEngine = visualRules.takeIf { it.isNotEmpty() }?.let { ReplacementEngine(it) }` — a `private var visualEngine: ReplacementEngine? = null` field set here; Task 6 reads it.)

- [ ] **Step 6: Build + suite + lint + commit**

Run: `./gradlew :app:testDebugUnitTest --tests "*ReplacementInjectorTest"` then `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/reader/ app/src/test/kotlin/com/nextz/kanomtts/reader/engine/ReplacementInjectorTest.kt
git commit -m "feat(reader): ReplacementInjector display transform threaded into openForDisplay"
```
**Device note:** with `visualReplacement` on + a `showInReader` rule, the replaced text renders (composed with Thai word-break); the highlight is fixed in Task 6.

---

## Task 6: Keep the TTS highlight aligned (quote transform)

**Files:**
- Create: `app/src/main/kotlin/com/nextz/kanomtts/reader/VisualHighlight.kt`
- Modify: `app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt` (highlight collector)
- Test: `app/src/test/kotlin/com/nextz/kanomtts/reader/VisualHighlightTest.kt`

**Interfaces:**
- Consumes: the `visualEngine` field set in Task 5's `load()`.
- Produces: `VisualHighlight.transformQuote(locator: Locator?, engine: ReplacementEngine?): Locator?`.

- [ ] **Step 1: Write the failing test**
```kotlin
package com.nextz.kanomtts.reader

import com.nextz.kanomtts.text.replacement.ReplacementEngine
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class VisualHighlightTest {
    private fun loc(h: String) = Locator(
        href = Url("c.xhtml")!!, mediaType = MediaType.XHTML,
        text = Locator.Text(highlight = h),
    )
    @Test fun `null engine or null locator returns input unchanged`() {
        val l = loc("hello")
        assertEquals(l, VisualHighlight.transformQuote(l, null))
        assertNull(VisualHighlight.transformQuote(null, ReplacementEngine(emptyList())))
    }
    @Test fun `engine rewrites the highlight quote`() {
        val engine = ReplacementEngine(listOf(ReplacementRule("hello", "hi", showInReader = true)))
        val out = VisualHighlight.transformQuote(loc("hello world"), engine)
        assertEquals("hi world", out!!.text.highlight)
    }
}
```

- [ ] **Step 2: Run — verify fail.** `./gradlew :app:testDebugUnitTest --tests "*VisualHighlightTest"` → FAIL.

- [ ] **Step 3: Implement `VisualHighlight`**
```kotlin
package com.nextz.kanomtts.reader

import com.nextz.kanomtts.text.replacement.ReplacementEngine
import org.readium.r2.shared.publication.Locator

/**
 * When Visual Replacement is on, the displayed text differs from the spoken sentence, so the
 * highlight's quote (which Readium matches against the rendered DOM) must be the REPLACED text.
 * Applies the same showInReader [engine] to the locator's highlight quote. Best-effort: if the
 * transformed quote does not match on device, Readium simply renders no highlight (no crash).
 */
object VisualHighlight {
    fun transformQuote(locator: Locator?, engine: ReplacementEngine?): Locator? {
        if (locator == null || engine == null) return locator
        val h = locator.text.highlight ?: return locator
        return locator.copy(text = locator.text.copy(highlight = engine.apply(h)))
    }
}
```

- [ ] **Step 4: Wire it into the highlight collector**

In `ReaderViewModel`'s TTS highlight collector, transform the locator before applying:
```kotlin
    viewModelScope.launch {
        ttsController.currentLocator.collect { loc ->
            val shown = VisualHighlight.transformQuote(loc, visualEngine)
            if (highlightOn) decorations.applyTts(shown) else decorations.applyTts(null)
            if (autoScroll && loc != null) {
                navigator?.takeIf { it.view != null }?.go(shown ?: loc, animated = false)
            }
        }
    }
```
(`visualEngine` is the field set in Task 5's `load()`; null when `visualReplacement` is off → no-op, identical to today.)

- [ ] **Step 5: Run + build + suite + lint + commit**

Run: `./gradlew :app:testDebugUnitTest --tests "*VisualHighlightTest"` then `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
```bash
git add app/src/main/kotlin/com/nextz/kanomtts/reader/VisualHighlight.kt app/src/main/kotlin/com/nextz/kanomtts/reader/ReaderViewModel.kt app/src/test/kotlin/com/nextz/kanomtts/reader/VisualHighlightTest.kt
git commit -m "feat(reader): re-derive TTS highlight quote from replaced text"
```

---

## Final integration pass (after Task 6)

- [ ] Full gate: `./gradlew :text:replacement:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` (run `:app:testDebugUnitTest --rerun-tasks` once) — all green.
- [ ] Opus whole-branch review; record Minors in `.superpowers/sdd/progress.md`.
- [ ] **Device matrix (the real validation):** mark a literal correction rule `showInReader`; enable `แทนที่ในหน้าอ่าน`; re-open the book → replaced text renders (composed with Thai word-break); **the rose karaoke highlight lands on the replaced sentence** (the crux — test with TTS playing); pronunciation (non-flagged) rules don't alter the page; toggling the pref takes effect on next open; TSV round-trips `showInReader`.
- [ ] `finishing-a-development-branch` (device sign-off gate, per the established cadence).

---

## Self-Review (completed during authoring)

**Spec coverage:** `showInReader` data model (Tasks 1–2) ↔ §3.1; editor/Add-Replacement toggle (Task 3) ↔ §3.2; `visualReplacement` pref + settings (Task 4) ↔ §3.3; `ReplacementInjector` + openForDisplay (Task 5) ↔ §3.4; highlight quote transform (Task 6) ↔ §3.5; migration (Task 2) ↔ §3.1.
**Deviation from spec (deliberate, recon-driven):** §3.6 said "reloads on toggle." Recon found `thaiWordBreak` does NOT live-reload today; matching it, `visualReplacement` is **apply-on-next-open** (Global Constraints + Task 5). This avoids a hard Readium live-fragment-swap. Flagged for the user at handoff.
**Type consistency:** `showInReader` (entity/domain/mapper/TSV/sheet/VM), `observeVisualEffective`, `visualReplacement`, `ReplacementInjector.inject`, `VisualHighlight.transformQuote`, `visualEngine`, the 4-arg `onSave` / `addRule` / 5-arg `addReplacement` are used consistently across producing and consuming tasks.
**Implementer must confirm on first open:** whether `exportSchema=true` needs handling on the version bump (Task 2 note); the exact `ReplacementEditorScreen` add/edit sheet call sites + any existing test calling the old `addRule` arity (Task 3 Step 5); the two `FakeReaderPreferences` locations (Task 4).
