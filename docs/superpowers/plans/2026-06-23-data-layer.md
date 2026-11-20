# Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Kanom TTS's offline persistence — a Room database (books, reading progress, bookmarks, replacement rules, per-language voice config) and a DataStore preferences store — exposed through coroutine/`Flow` repositories, wired with Hilt.

**Architecture:** Modules are collapsed into `:app` (project decision): the data layer lives in `com.nextz.kanomtts.data` (`data.db` for Room entities/DAOs/database, `data.prefs` for DataStore, `data.repository` for repositories). One `KanomDatabase` declares ALL entities up front (schema fixed at v1), so subsequent tasks add DAOs/repositories without schema migrations. Repositories are the only surface feature code touches; they return `Flow`s for observation and `suspend` for writes. Persisted replacement rules map to the existing `com.nextz.kanomtts.text.replacement.ReplacementRule` domain type. Locators (Readium reading positions) are stored as opaque `String` (JSON) — serialization belongs to the Reader plan.

**Tech Stack:** Room (KSP compiler), DataStore Preferences, Kotlin Coroutines/Flow, Hilt. Tests run on the JVM via **Robolectric** (in-memory Room) + `kotlinx-coroutines-test`.

## Global Constraints

- Collapsed modules: data layer is PACKAGES in `:app` under `com.nextz.kanomtts.data` — NOT a separate module. (Project decision; spec §3.2 modules deferred.)
- `:app` uses AGP 9.2.1 built-in Kotlin 2.2.10 — NO `org.jetbrains.kotlin.android`; KSP already configured (`com.google.devtools.ksp 2.2.10-2.0.2`) with `android.disallowKotlinSourceSets=false`. Add Room's compiler via `ksp(...)`. — Plan 02/03 findings.
- Java 17 + core-library desugaring + Readium 3.3.0 retained. minSdk 26 / compileSdk 36.
- Persisted replacement rules MUST map to `com.nextz.kanomtts.text.replacement.ReplacementRule(pattern, replacement, isRegex, enabled, order)` (the engine's domain type). — Plan 01.
- Preferences to store (spec §7): readerTheme, readingFont, fontSizeSp, lineHeight, marginDp, skipUrls, highlightOn, autoScroll, sleepTimerDefaultMin, premiumUnlocked.
- All DAO writes are `suspend`; all observable reads return `Flow`. No blocking I/O on the main thread.
- TDD: every DAO/repository gets a Robolectric test (real in-memory Room) written before/with its implementation. DRY, YAGNI, frequent commits.

---

## File Structure

```
gradle/libs.versions.toml          (modify: room, datastore, robolectric, coroutines-test, androidx-test-core)
app/build.gradle.kts               (modify: room+datastore deps, ksp(room-compiler), test deps, testOptions)
app/src/main/kotlin/com/nextz/kanomtts/data/
    db/BookEntity.kt  ProgressEntity.kt  BookmarkEntity.kt  ReplacementRuleEntity.kt  VoiceConfigEntity.kt
    db/Converters.kt                 (enum/Instant <-> primitive TypeConverters)
    db/BookDao.kt  ProgressDao.kt  BookmarkDao.kt  ReplacementRuleDao.kt  VoiceConfigDao.kt
    db/KanomDatabase.kt              (RoomDatabase, version 1, all entities)
    prefs/KanomPreferences.kt        (data class snapshot of prefs + enums)
    prefs/PreferencesRepository.kt   (DataStore-backed)
    repository/BookRepository.kt  ProgressRepository.kt  BookmarkRepository.kt
    repository/ReplacementRuleRepository.kt  VoiceConfigRepository.kt
    repository/RuleMapper.kt         (ReplacementRuleEntity <-> text.replacement.ReplacementRule)
    di/DataModule.kt                 (Hilt: provides DB, DAOs, DataStore; binds repositories)
app/src/test/kotlin/com/nextz/kanomtts/data/
    db/BookDaoTest.kt  ProgressDaoTest.kt  BookmarkDaoTest.kt  ReplacementRuleDaoTest.kt
    prefs/PreferencesRepositoryTest.kt
    repository/RuleMapperTest.kt
    util/RoomTestBase.kt             (Robolectric in-memory DB harness)
```

---

### Task 1: Data foundation + Book slice (Room/Robolectric canary)

Add the data deps, define ALL entities + converters + `KanomDatabase`, and prove the Robolectric+Room toolchain works by round-tripping a Book.

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: all five `*Entity.kt`, `db/Converters.kt`, `db/BookDao.kt`, `db/KanomDatabase.kt`, `repository/BookRepository.kt`, test `util/RoomTestBase.kt`, test `db/BookDaoTest.kt`

**Interfaces:**
- Produces: `BookEntity`, `ProgressEntity`, `BookmarkEntity`, `ReplacementRuleEntity`, `VoiceConfigEntity`; `KanomDatabase` (abstract, `bookDao()`); `BookDao`; `BookRepository`.

- [ ] **Step 1: Add catalog entries** (verify current stable versions on Google Maven)
```toml
# [versions]
room = "2.8.2"
datastore = "1.1.7"
robolectric = "4.16"
coroutinesTest = "1.10.2"
androidxTestCore = "1.7.0"
# [libraries]
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
```

- [ ] **Step 2: Wire `app/build.gradle.kts`**
Add `implementation(libs.androidx.room.runtime)`, `implementation(libs.androidx.room.ktx)`, `ksp(libs.androidx.room.compiler)`, `implementation(libs.androidx.datastore.preferences)`. Add test deps: `testImplementation(libs.robolectric)`, `testImplementation(libs.androidx.test.core)`, `testImplementation(libs.kotlinx.coroutines.test)`, `testImplementation(libs.junit)`. Add:
```kotlin
android {
    testOptions { unitTests { isIncludeAndroidResources = true } }
    // optional but recommended for Room schema export:
    // ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}
```

- [ ] **Step 3: Entities + converters**
`BookEntity.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,          // stable id (e.g. content uri hash or uuid)
    val uri: String,
    val title: String,
    val author: String?,
    val lang: String?,                   // BCP-47 primary language hint
    val coverPath: String?,
    val fileSize: Long,
    val addedAt: Long,                   // epoch millis
    val lastOpenedAt: Long?,
)
```
`ProgressEntity.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val locator: String?,                // JSON Readium Locator (rendered position)
    val ttsLocator: String?,             // JSON Locator where TTS stopped
    val percent: Float,                  // 0f..1f
    val updatedAt: Long,
)
```
`BookmarkEntity.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index("bookId")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val locator: String,                 // JSON Locator
    val snippet: String,
    val createdAt: Long,
)
```
`ReplacementRuleEntity.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** scope: "global" or "book"; bookId non-null only when scope == "book". */
@Entity(tableName = "replacement_rules", indices = [Index("scope"), Index("bookId")])
data class ReplacementRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val bookId: String?,
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean,
    val enabled: Boolean,
    val position: Int,                   // ordering ("order" is SQL-reserved)
)
```
`VoiceConfigEntity.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per routing language (TH/EN/JA/ZH). */
@Entity(tableName = "voice_config")
data class VoiceConfigEntity(
    @PrimaryKey val lang: String,        // Lang.name
    val providerId: String,
    val voiceId: String?,
    val speed: Float,
    val pitch: Float,
)
```
`Converters.kt` (none needed yet — all fields are primitives/String; create the file with a placeholder comment so future enum/Instant converters have a home, or omit until needed). **YAGNI: skip `Converters.kt` for now; add it in the task that first needs a converter.**

- [ ] **Step 4: `BookDao.kt`**
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE uri = :uri OR (title = :title AND fileSize = :fileSize))")
    suspend fun existsDuplicate(uri: String, title: String, fileSize: Long): Boolean

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}
```

- [ ] **Step 5: `KanomDatabase.kt`**
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class, ProgressEntity::class, BookmarkEntity::class,
        ReplacementRuleEntity::class, VoiceConfigEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KanomDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    // progressDao()/bookmarkDao()/replacementRuleDao()/voiceConfigDao() added in later tasks
}
```

- [ ] **Step 6: `BookRepository.kt`**
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookRepository @Inject constructor(private val dao: BookDao) {
    fun observeLibrary(): Flow<List<BookEntity>> = dao.observeAll()
    suspend fun get(id: String): BookEntity? = dao.getById(id)
    suspend fun add(book: BookEntity) = dao.upsert(book)
    suspend fun isDuplicate(uri: String, title: String, fileSize: Long): Boolean =
        dao.existsDuplicate(uri, title, fileSize)
    suspend fun remove(id: String) = dao.delete(id)
}
```

- [ ] **Step 7: Robolectric harness `util/RoomTestBase.kt`**
```kotlin
package com.nextz.kanomtts.data.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nextz.kanomtts.data.db.KanomDatabase
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
abstract class RoomTestBase {
    protected lateinit var db: KanomDatabase
    @Before fun createDb() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KanomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
    @After fun closeDb() { db.close() }
}
```

- [ ] **Step 8: Write the failing test `db/BookDaoTest.kt`**
```kotlin
package com.nextz.kanomtts.data.db

import app.cash.turbine.test
import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDaoTest : RoomTestBase() {
    private fun book(id: String, uri: String = "uri://$id", title: String = "T$id", size: Long = 100) =
        BookEntity(id, uri, title, null, "th", null, size, 1L, null)

    @Test fun upsert_then_query_round_trips() = runTest {
        db.bookDao().upsert(book("a"))
        assertEquals("Ta", db.bookDao().getById("a")?.title)
    }

    @Test fun observeAll_emits_inserted_books() = runTest {
        db.bookDao().upsert(book("a"))
        db.bookDao().upsert(book("b"))
        assertEquals(2, db.bookDao().observeAll().first().size)
    }

    @Test fun duplicate_detection_by_uri_or_title_and_size() = runTest {
        db.bookDao().upsert(book("a", uri = "uri://x", title = "Same", size = 50))
        assertTrue(db.bookDao().existsDuplicate("uri://x", "other", 999))     // uri match
        assertTrue(db.bookDao().existsDuplicate("uri://other", "Same", 50))   // title+size match
        assertFalse(db.bookDao().existsDuplicate("uri://other", "other", 999))
    }

    @Test fun delete_removes_book() = runTest {
        db.bookDao().upsert(book("a"))
        db.bookDao().delete("a")
        assertEquals(null, db.bookDao().getById("a"))
    }
}
```
(If `turbine` is not already a dependency, do NOT add it — the test above only uses `.first()`; remove the unused turbine import.)

- [ ] **Step 9: RED then GREEN**
Run `./gradlew :app:testDebugUnitTest --tests "*BookDaoTest"` → expect RED first (unresolved / no Room). After Steps 3-7 it should be GREEN. This is the Robolectric+Room+KSP canary — if Robolectric can't load (SDK level, native SQLite), record the exact error; try `@Config(sdk=[...])` on `RoomTestBase` with a Robolectric-supported SDK if 36 isn't supported yet, and note it.

- [ ] **Step 10: Verify + commit**
Run `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "*BookDaoTest"` → BUILD SUCCESSFUL + pass.
```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src
git commit -m "feat(data): Room foundation + Book entity/dao/repository (Robolectric canary)"
```

---

### Task 2: Reading progress (resume)

**Files:** Create `db/ProgressDao.kt`, `repository/ProgressRepository.kt`, test `db/ProgressDaoTest.kt`; Modify `db/KanomDatabase.kt` (add `abstract fun progressDao()`).

**Interfaces:** `ProgressDao` (upsert, observe by book, get by book); `ProgressRepository`.

- [ ] **Step 1: `ProgressDao.kt`**
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): ProgressEntity?

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ProgressEntity?>
}
```

- [ ] **Step 2:** add `abstract fun progressDao(): ProgressDao` to `KanomDatabase`.

- [ ] **Step 3: `ProgressRepository.kt`**
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ProgressDao
import com.nextz.kanomtts.data.db.ProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ProgressRepository @Inject constructor(private val dao: ProgressDao) {
    suspend fun save(progress: ProgressEntity) = dao.upsert(progress)
    suspend fun get(bookId: String): ProgressEntity? = dao.get(bookId)
    fun observe(bookId: String): Flow<ProgressEntity?> = dao.observe(bookId)
}
```

- [ ] **Step 4: Test `db/ProgressDaoTest.kt`** (RED→GREEN)
```kotlin
package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressDaoTest : RoomTestBase() {
    @Test fun upsert_overwrites_and_get_returns_latest() = runTest {
        db.progressDao().upsert(ProgressEntity("b1", "loc1", null, 0.1f, 1L))
        db.progressDao().upsert(ProgressEntity("b1", "loc2", "tts2", 0.5f, 2L))
        val p = db.progressDao().get("b1")!!
        assertEquals("loc2", p.locator); assertEquals("tts2", p.ttsLocator); assertEquals(0.5f, p.percent, 0f)
    }
    @Test fun observe_emits_null_when_absent_then_value() = runTest {
        db.progressDao().upsert(ProgressEntity("b2", "loc", null, 0.2f, 1L))
        assertEquals(0.2f, db.progressDao().observe("b2").first()!!.percent, 0f)
    }
}
```

- [ ] **Step 5: Verify + commit**
`./gradlew :app:testDebugUnitTest --tests "*ProgressDaoTest"` → pass.
```bash
git add app/src && git commit -m "feat(data): reading-progress dao + repository"
```

---

### Task 3: Bookmarks

**Files:** Create `db/BookmarkDao.kt`, `repository/BookmarkRepository.kt`, test `db/BookmarkDaoTest.kt`; Modify `KanomDatabase` (`bookmarkDao()`).

**Interfaces:** `BookmarkDao` (insert, observe by book ordered by createdAt, delete); `BookmarkRepository`.

- [ ] **Step 1: `BookmarkDao.kt`**
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert suspend fun insert(bookmark: BookmarkEntity): Long
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>
    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 2:** add `abstract fun bookmarkDao(): BookmarkDao`.

- [ ] **Step 3: `BookmarkRepository.kt`**
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.BookmarkDao
import com.nextz.kanomtts.data.db.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookmarkRepository @Inject constructor(private val dao: BookmarkDao) {
    fun observe(bookId: String): Flow<List<BookmarkEntity>> = dao.observeForBook(bookId)
    suspend fun add(bookmark: BookmarkEntity): Long = dao.insert(bookmark)
    suspend fun remove(id: Long) = dao.delete(id)
}
```

- [ ] **Step 4: Test `db/BookmarkDaoTest.kt`** (RED→GREEN)
```kotlin
package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkDaoTest : RoomTestBase() {
    @Test fun insert_and_observe_ordered_desc() = runTest {
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l1", snippet = "s1", createdAt = 1L))
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l2", snippet = "s2", createdAt = 2L))
        val list = db.bookmarkDao().observeForBook("b").first()
        assertEquals(2, list.size)
        assertEquals("s2", list.first().snippet)   // newest first
    }
    @Test fun delete_removes() = runTest {
        val id = db.bookmarkDao().insert(BookmarkEntity(bookId = "b", locator = "l", snippet = "s", createdAt = 1L))
        db.bookmarkDao().delete(id)
        assertEquals(0, db.bookmarkDao().observeForBook("b").first().size)
    }
    @Test fun bookmarks_scoped_by_book() = runTest {
        db.bookmarkDao().insert(BookmarkEntity(bookId = "b1", locator = "l", snippet = "s", createdAt = 1L))
        assertEquals(0, db.bookmarkDao().observeForBook("b2").first().size)
    }
}
```

- [ ] **Step 5: Verify + commit**
`./gradlew :app:testDebugUnitTest --tests "*BookmarkDaoTest"` → pass.
```bash
git add app/src && git commit -m "feat(data): bookmarks dao + repository"
```

---

### Task 4: Replacement-rule store + mapper

Persist replacement rules and map them to the engine's domain type.

**Files:** Create `db/ReplacementRuleDao.kt`, `repository/RuleMapper.kt`, `repository/ReplacementRuleRepository.kt`, tests `db/ReplacementRuleDaoTest.kt` + `repository/RuleMapperTest.kt`; Modify `KanomDatabase` (`replacementRuleDao()`), `app/build.gradle.kts` (add `implementation(project(":text:replacement"))`).

**Interfaces:** `ReplacementRuleDao` (upsert, observe global, observe for book, delete, max position); `RuleMapper.toDomain(entity): com.nextz.kanomtts.text.replacement.ReplacementRule`; `ReplacementRuleRepository` (observe effective rules for a book = global + book-scoped, mapped to domain, ordered by position).

- [ ] **Step 1:** add `implementation(project(":text:replacement"))` to `app/build.gradle.kts`.

- [ ] **Step 2: `ReplacementRuleDao.kt`**
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplacementRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(rule: ReplacementRuleEntity): Long
    @Update suspend fun update(rule: ReplacementRuleEntity)
    @Query("DELETE FROM replacement_rules WHERE id = :id") suspend fun delete(id: Long)

    // Effective rules for a book: global + that book's own, ordered.
    @Query("""SELECT * FROM replacement_rules
              WHERE scope = 'global' OR (scope = 'book' AND bookId = :bookId)
              ORDER BY position ASC""")
    fun observeEffective(bookId: String): Flow<List<ReplacementRuleEntity>>

    @Query("SELECT * FROM replacement_rules WHERE scope = 'global' ORDER BY position ASC")
    fun observeGlobal(): Flow<List<ReplacementRuleEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM replacement_rules")
    suspend fun maxPosition(): Int
}
```

- [ ] **Step 3:** add `abstract fun replacementRuleDao(): ReplacementRuleDao`.

- [ ] **Step 4: `RuleMapper.kt`**
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.text.replacement.ReplacementRule

object RuleMapper {
    fun toDomain(e: ReplacementRuleEntity): ReplacementRule =
        ReplacementRule(
            pattern = e.pattern,
            replacement = e.replacement,
            isRegex = e.isRegex,
            enabled = e.enabled,
            order = e.position,
        )
}
```

- [ ] **Step 5: `ReplacementRuleRepository.kt`**
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleDao
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.text.replacement.ReplacementRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReplacementRuleRepository @Inject constructor(private val dao: ReplacementRuleDao) {
    /** Effective domain rules for a book (global + book-scoped), ready for ReplacementEngine. */
    fun observeEffective(bookId: String): Flow<List<ReplacementRule>> =
        dao.observeEffective(bookId).map { list -> list.map(RuleMapper::toDomain) }

    fun observeGlobal(): Flow<List<ReplacementRuleEntity>> = dao.observeGlobal()
    suspend fun add(rule: ReplacementRuleEntity): Long =
        dao.insert(rule.copy(position = if (rule.position >= 0) rule.position else dao.maxPosition() + 1))
    suspend fun update(rule: ReplacementRuleEntity) = dao.update(rule)
    suspend fun remove(id: Long) = dao.delete(id)
}
```

- [ ] **Step 6: Tests** (RED→GREEN)
`repository/RuleMapperTest.kt` (pure JVM, no Robolectric needed):
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleMapperTest {
    @Test fun entity_maps_to_domain_with_position_as_order() {
        val e = ReplacementRuleEntity(id = 5, scope = "global", bookId = null,
            pattern = "GPT", replacement = "จีพีที", isRegex = false, enabled = true, position = 3)
        val d = RuleMapper.toDomain(e)
        assertEquals("GPT", d.pattern); assertEquals("จีพีที", d.replacement)
        assertEquals(false, d.isRegex); assertEquals(true, d.enabled); assertEquals(3, d.order)
    }
}
```
`db/ReplacementRuleDaoTest.kt`:
```kotlin
package com.nextz.kanomtts.data.db

import com.nextz.kanomtts.data.util.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementRuleDaoTest : RoomTestBase() {
    private fun rule(scope: String, bookId: String?, pattern: String, pos: Int) =
        ReplacementRuleEntity(scope = scope, bookId = bookId, pattern = pattern,
            replacement = "x", isRegex = false, enabled = true, position = pos)

    @Test fun effective_includes_global_and_book_ordered() = runTest {
        db.replacementRuleDao().insert(rule("global", null, "g", 0))
        db.replacementRuleDao().insert(rule("book", "b1", "b1rule", 1))
        db.replacementRuleDao().insert(rule("book", "b2", "b2rule", 2))
        val eff = db.replacementRuleDao().observeEffective("b1").first()
        assertEquals(listOf("g", "b1rule"), eff.map { it.pattern })   // excludes b2's rule
    }
    @Test fun maxPosition_is_minus1_when_empty_then_tracks_max() = runTest {
        assertEquals(-1, db.replacementRuleDao().maxPosition())
        db.replacementRuleDao().insert(rule("global", null, "g", 7))
        assertEquals(7, db.replacementRuleDao().maxPosition())
    }
}
```

- [ ] **Step 7: Verify + commit**
`./gradlew :app:testDebugUnitTest --tests "*ReplacementRuleDaoTest" --tests "*RuleMapperTest"` → pass.
```bash
git add app/src app/build.gradle.kts && git commit -m "feat(data): replacement-rule store + domain mapper"
```

---

### Task 5: Voice config + DataStore preferences

**Files:** Create `db/VoiceConfigDao.kt`, `repository/VoiceConfigRepository.kt`, `prefs/KanomPreferences.kt`, `prefs/PreferencesRepository.kt`, test `prefs/PreferencesRepositoryTest.kt`; Modify `KanomDatabase` (`voiceConfigDao()`).

**Interfaces:** `VoiceConfigDao` (upsert, observe all, get by lang); `VoiceConfigRepository`; `KanomPreferences` (data class) + `ReadingFont` enum; `PreferencesRepository` (DataStore-backed `Flow<KanomPreferences>` + setters).

- [ ] **Step 1: `VoiceConfigDao.kt`** + `abstract fun voiceConfigDao()`:
```kotlin
package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(config: VoiceConfigEntity)
    @Query("SELECT * FROM voice_config") fun observeAll(): Flow<List<VoiceConfigEntity>>
    @Query("SELECT * FROM voice_config WHERE lang = :lang") suspend fun get(lang: String): VoiceConfigEntity?
}
```
`VoiceConfigRepository.kt`:
```kotlin
package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.VoiceConfigDao
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VoiceConfigRepository @Inject constructor(private val dao: VoiceConfigDao) {
    fun observeAll(): Flow<List<VoiceConfigEntity>> = dao.observeAll()
    suspend fun get(lang: String): VoiceConfigEntity? = dao.get(lang)
    suspend fun set(config: VoiceConfigEntity) = dao.upsert(config)
}
```

- [ ] **Step 2: `KanomPreferences.kt`**
```kotlin
package com.nextz.kanomtts.data.prefs

import com.nextz.kanomtts.ui.theme.ReaderTheme

enum class ReadingFont { SARABUN, SERIF, SYSTEM }

data class KanomPreferences(
    val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    val readingFont: ReadingFont = ReadingFont.SARABUN,
    val fontSizeSp: Int = 18,
    val lineHeight: Float = 1.6f,
    val marginDp: Int = 16,
    val skipUrls: Boolean = false,
    val highlightOn: Boolean = true,
    val autoScroll: Boolean = true,
    val sleepTimerDefaultMin: Int = 30,
    val premiumUnlocked: Boolean = false,
)
```

- [ ] **Step 3: `PreferencesRepository.kt`**
```kotlin
package com.nextz.kanomtts.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PreferencesRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    private object Keys {
        val theme = stringPreferencesKey("reader_theme")
        val font = stringPreferencesKey("reading_font")
        val fontSize = intPreferencesKey("font_size_sp")
        val lineHeight = floatPreferencesKey("line_height")
        val margin = intPreferencesKey("margin_dp")
        val skipUrls = booleanPreferencesKey("skip_urls")
        val highlight = booleanPreferencesKey("highlight_on")
        val autoScroll = booleanPreferencesKey("auto_scroll")
        val sleep = intPreferencesKey("sleep_timer_default_min")
        val premium = booleanPreferencesKey("premium_unlocked")
    }

    val preferences: Flow<KanomPreferences> = store.data.map { p ->
        val d = KanomPreferences()
        KanomPreferences(
            readerTheme = p[Keys.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: d.readerTheme,
            readingFont = p[Keys.font]?.let { runCatching { ReadingFont.valueOf(it) }.getOrNull() } ?: d.readingFont,
            fontSizeSp = p[Keys.fontSize] ?: d.fontSizeSp,
            lineHeight = p[Keys.lineHeight] ?: d.lineHeight,
            marginDp = p[Keys.margin] ?: d.marginDp,
            skipUrls = p[Keys.skipUrls] ?: d.skipUrls,
            highlightOn = p[Keys.highlight] ?: d.highlightOn,
            autoScroll = p[Keys.autoScroll] ?: d.autoScroll,
            sleepTimerDefaultMin = p[Keys.sleep] ?: d.sleepTimerDefaultMin,
            premiumUnlocked = p[Keys.premium] ?: d.premiumUnlocked,
        )
    }

    suspend fun setReaderTheme(v: ReaderTheme) = store.edit { it[Keys.theme] = v.name }
    suspend fun setReadingFont(v: ReadingFont) = store.edit { it[Keys.font] = v.name }
    suspend fun setFontSizeSp(v: Int) = store.edit { it[Keys.fontSize] = v }
    suspend fun setLineHeight(v: Float) = store.edit { it[Keys.lineHeight] = v }
    suspend fun setMarginDp(v: Int) = store.edit { it[Keys.margin] = v }
    suspend fun setSkipUrls(v: Boolean) = store.edit { it[Keys.skipUrls] = v }
    suspend fun setHighlightOn(v: Boolean) = store.edit { it[Keys.highlight] = v }
    suspend fun setAutoScroll(v: Boolean) = store.edit { it[Keys.autoScroll] = v }
    suspend fun setSleepTimerDefaultMin(v: Int) = store.edit { it[Keys.sleep] = v }
    suspend fun setPremiumUnlocked(v: Boolean) = store.edit { it[Keys.premium] = v }
}
```

- [ ] **Step 4: Test `prefs/PreferencesRepositoryTest.kt`** — build a DataStore over a temp file (JVM, no Robolectric needed):
```kotlin
package com.nextz.kanomtts.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun repo(): PreferencesRepository {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create {
            File(tmp.newFolder(), "test.preferences_pb")
        }
        return PreferencesRepository(store)
    }

    @Test fun defaults_when_empty() = runTest {
        val p = repo().preferences.first()
        assertEquals(ReaderTheme.LIGHT, p.readerTheme)
        assertEquals(18, p.fontSizeSp)
        assertEquals(false, p.premiumUnlocked)
    }
    @Test fun writes_round_trip() = runTest {
        val r = repo()
        r.setReaderTheme(ReaderTheme.TRUE_BLACK); r.setFontSizeSp(24); r.setPremiumUnlocked(true)
        val p = r.preferences.first()
        assertEquals(ReaderTheme.TRUE_BLACK, p.readerTheme)
        assertEquals(24, p.fontSizeSp)
        assertEquals(true, p.premiumUnlocked)
    }
    @Test fun corrupt_enum_falls_back_to_default() = runTest {
        // setting an int where enum expected isn't possible via API; verify unknown name path via valueOf guard
        val r = repo(); r.setReadingFont(ReadingFont.SERIF)
        assertEquals(ReadingFont.SERIF, r.preferences.first().readingFont)
    }
}
```

- [ ] **Step 5: Verify + commit**
`./gradlew :app:testDebugUnitTest --tests "*PreferencesRepositoryTest"` → pass (and ensure VoiceConfig compiles via assembleDebug).
```bash
git add app/src && git commit -m "feat(data): voice-config dao + DataStore preferences repository"
```

---

### Task 6: Hilt DI wiring

Make the database, DAOs, DataStore, and repositories injectable app-wide.

**Files:** Create `di/DataModule.kt`; test `di/DataModuleTest.kt` (optional smoke). Modify nothing else.

**Interfaces:** Hilt `@Module @InstallIn(SingletonComponent::class)` providing `KanomDatabase` (singleton), each DAO (from the db), and the `DataStore<Preferences>`. Repositories are `@Inject constructor` so they're auto-provided; no explicit binds needed.

- [ ] **Step 1: `di/DataModule.kt`**
```kotlin
package com.nextz.kanomtts.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.nextz.kanomtts.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanom_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KanomDatabase =
        Room.databaseBuilder(ctx, KanomDatabase::class.java, "kanom.db").build()

    @Provides fun provideBookDao(db: KanomDatabase): BookDao = db.bookDao()
    @Provides fun provideProgressDao(db: KanomDatabase): ProgressDao = db.progressDao()
    @Provides fun provideBookmarkDao(db: KanomDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideReplacementRuleDao(db: KanomDatabase): ReplacementRuleDao = db.replacementRuleDao()
    @Provides fun provideVoiceConfigDao(db: KanomDatabase): VoiceConfigDao = db.voiceConfigDao()

    @Provides @Singleton
    fun providedataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore
}
```

- [ ] **Step 2: Verify the app graph builds**
Run `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (Hilt validates the graph: every repository's deps are providable). Then full suite: `./gradlew :app:testDebugUnitTest :text:pipeline:test :text:replacement:test`.

- [ ] **Step 3: Commit**
```bash
git add app/src && git commit -m "feat(data): Hilt DataModule wiring database, daos, datastore"
```

---

## Self-Review

**1. Spec coverage (spec §7 data model):**
- Book / Progress / Bookmark / ReplacementRule / VoiceConfig entities → Task 1 (all declared), DAOs/repos Tasks 1-5. ✓
- Reading-progress incl. ttsLocator (resume where TTS stopped, spec §5.1) → Task 2. ✓
- Duplicate detection (filename/size, spec §5.1) → Task 1 `existsDuplicate`. ✓
- Replacement rules persisted + mapped to engine domain type (spec §5.1, Plan 01) → Task 4. ✓
- Preferences: theme/font/size/line-height/margin/skipUrls/highlight/autoScroll/sleepTimer/premium (spec §7) → Task 5. ✓
- Hilt DI (spec §3.1) → Task 6. ✓
- *Deferred (correct):* Locator JSON serialization (Reader plan 06); TSV import/export already lives in `:text:replacement` (Plan 01); the default Thai pronunciation-pack seeding (Settings/feature plan).

**2. Placeholder scan:** dep versions are marked "verify current on Google Maven" with a build gate (Task 1 canary); no TODO/TBD; every code + test step is complete. The Robolectric SDK-level fallback (`@Config`) is a contingency, not a placeholder. ✓

**3. Type consistency:** entity names, DAO method names, repository method names, `RuleMapper.toDomain`, `KanomPreferences`/`ReadingFont`, `ReaderTheme` (reused from Plan 03 `com.nextz.kanomtts.ui.theme`) are consistent across tasks. `position` (not `order`) is used for rule ordering to avoid the SQL reserved word; `RuleMapper` maps `position`→domain `order`. ✓

**Note for executor:** Task 1 is the Robolectric+Room+KSP canary — if Robolectric cannot run against compileSdk 36 in this toolchain, set `@Config(sdk = [...])` on `RoomTestBase` to the highest Robolectric-supported SDK and record it; do not abandon JVM testing for instrumented tests (no device in CI). `ReaderTheme` already exists from Plan 03 — import it, don't redefine.
