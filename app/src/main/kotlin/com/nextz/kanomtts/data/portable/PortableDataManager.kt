package com.nextz.kanomtts.data.portable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.withTransaction
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.data.db.BookTagEntity
import com.nextz.kanomtts.data.db.BookNoteEntity
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.db.ReadingSessionEntity
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

internal fun isRestorablePortableEntry(name: String): Boolean = name == "data.json" || name.startsWith("covers/")

data class PortableBackupSummary(
    val books: Int,
    val progress: Int,
    val bookmarks: Int,
    val replacementRules: Int,
    val voiceConfigs: Int,
    val tags: Int,
    val notes: Int,
    val readingSessions: Int,
    val backupFile: String,
)

@Singleton
class PortableDataManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: KanomDatabase,
    private val dataStore: DataStore<Preferences>,
) {
    private val root = File(Environment.getExternalStorageDirectory(), "Kanom")
    private val backupDir get() = File(root, "backup")
    private val latestBackup get() = File(backupDir, "kanom-latest.zip")

    private data class DbSnapshot(
        val books: List<BookEntity>,
        val progress: List<ProgressEntity>,
        val bookmarks: List<BookmarkEntity>,
        val rules: List<ReplacementRuleEntity>,
        val voices: List<VoiceConfigEntity>,
        val tags: List<BookTagEntity>,
        val notes: List<BookNoteEntity>,
        val sessions: List<ReadingSessionEntity>,
    )

    fun hasStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun portableFolderPath(): String = root.absolutePath

    fun ensurePortableFolders(): Boolean {
        if (!hasStorageAccess()) return false
        // Portable metadata is stored together in data.json inside the backup ZIP.
        // Create only folders that have an actual on-disk purpose.
        return listOf(root, backupDir).all { it.exists() || it.mkdirs() }
    }

    fun latestBackupExists(): Boolean = hasStorageAccess() && latestBackup.isFile

    suspend fun createBackup(): PortableBackupSummary = withContext(Dispatchers.IO) {
        requireStorageAccess()
        check(ensurePortableFolders()) { "สร้างโฟลเดอร์ Kanom ไม่สำเร็จ" }
        val snapshot = database.withTransaction {
            val books = database.bookDao().getAllForBackup()
            val bookIds = books.map { it.id }.toSet()
            DbSnapshot(
                books = books,
                progress = database.progressDao().getAllForBackup(),
                bookmarks = database.bookmarkDao().getAllForBackup(),
                rules = database.replacementRuleDao().getAllForBackup(),
                voices = database.voiceConfigDao().getAllForBackup(),
                tags = database.bookTagDao().getAllForBackup(),
                notes = database.bookNoteDao().getAllForBackup(),
                sessions = database.readingSessionDao().getAllForBackup().filter { it.bookId in bookIds },
            )
        }
        val books = snapshot.books
        val progress = snapshot.progress
        val bookmarks = snapshot.bookmarks
        val rules = snapshot.rules
        val voices = snapshot.voices
        val tags = snapshot.tags
        val notes = snapshot.notes
        val sessions = snapshot.sessions
        val prefs = dataStore.data.first()
        val payload = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("books", JSONArray().apply {
                books.forEach { book ->
                    val coverFile = book.coverPath?.let(::File)?.takeIf { it.isFile }
                    val coverEntry = coverFile?.let { "covers/${safeName(book.id)}.bin" }
                    put(JSONObject().apply {
                        put("id", book.id); put("uri", book.uri); put("title", book.title)
                        putNullable("author", book.author); putNullable("lang", book.lang)
                        putNullable("coverEntry", coverEntry)
                        put("fileSize", book.fileSize)
                        putNullable("sha256", book.sha256)
                        put("addedAt", book.addedAt); putNullable("lastOpenedAt", book.lastOpenedAt)
                        putNullable("sourceFolder", book.sourceFolder); putNullable("sourceFileName", book.sourceFileName)
                    })
                }
            })
            put("progress", JSONArray().apply {
                progress.forEach { p -> put(JSONObject().apply {
                    put("bookId", p.bookId); putNullable("locator", p.locator)
                    putNullable("ttsLocator", p.ttsLocator); put("percent", p.percent.toDouble())
                    put("updatedAt", p.updatedAt)
                }) }
            })
            put("bookmarks", JSONArray().apply {
                bookmarks.forEach { b -> put(JSONObject().apply {
                    put("id", b.id); put("bookId", b.bookId); put("locator", b.locator)
                    put("snippet", b.snippet); put("createdAt", b.createdAt)
                }) }
            })
            put("replacementRules", JSONArray().apply {
                rules.forEach { r -> put(JSONObject().apply {
                    put("id", r.id); put("scope", r.scope); putNullable("bookId", r.bookId)
                    put("pattern", r.pattern); put("replacement", r.replacement)
                    put("isRegex", r.isRegex); put("enabled", r.enabled)
                    put("position", r.position); put("showInReader", r.showInReader)
                }) }
            })
            put("voiceConfigs", JSONArray().apply {
                voices.forEach { v -> put(JSONObject().apply {
                    put("lang", v.lang); put("providerId", v.providerId)
                    putNullable("voiceId", v.voiceId); put("speed", v.speed.toDouble())
                    put("pitch", v.pitch.toDouble())
                }) }
            })
            put("bookTags", JSONArray().apply {
                tags.forEach { tag -> put(JSONObject().put("bookId", tag.bookId).put("tag", tag.tag)) }
            })
            put("bookNotes", JSONArray().apply {
                notes.forEach { note -> put(JSONObject().put("bookId", note.bookId)
                    .put("note", note.note).put("updatedAt", note.updatedAt)) }
            })
            put("readingSessions", JSONArray().apply {
                sessions.forEach { session -> put(JSONObject().put("id", session.id)
                    .put("bookId", session.bookId).put("startedAt", session.startedAt)
                    .put("durationMs", session.durationMs)) }
            })
            put("preferences", encodePreferences(prefs))
        }

        val temp = File(backupDir, "kanom-latest.zip.tmp")
        ZipOutputStream(FileOutputStream(temp).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(payload.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            books.forEach { book ->
                val cover = book.coverPath?.let(::File)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry("covers/${safeName(book.id)}.bin"))
                cover.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        runCatching {
            Files.move(temp.toPath(), latestBackup.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.recoverCatching {
            Files.move(temp.toPath(), latestBackup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse { throw IllegalStateException("บันทึกไฟล์สำรองไม่สำเร็จ", it) }

        File(root, "manifest.json").writeText(
            JSONObject().put("formatVersion", FORMAT_VERSION)
                .put("lastBackupAt", System.currentTimeMillis())
                .put("latestBackup", "backup/kanom-latest.zip").toString(2),
            Charsets.UTF_8,
        )
        PortableBackupSummary(books.size, progress.size, bookmarks.size, rules.size, voices.size, tags.size, notes.size, sessions.size, latestBackup.absolutePath)
    }

    /**
     * Reads only backup metadata and returns the books that need source reconciliation.
     * This is a read-only preflight: it does not touch the database, preferences, or EPUB files.
     */
    suspend fun previewBackupBooks(): List<BookEntity> = withContext(Dispatchers.IO) {
        requireStorageAccess()
        check(latestBackup.isFile) { "ไม่พบไฟล์สำรอง Kanom" }
        val json = readBackupJson()
        val bookArray = json.getJSONArray("books")
        (0 until bookArray.length()).map { index ->
            val item = bookArray.getJSONObject(index)
            BookEntity(
                id = item.getString("id"),
                uri = item.getString("uri"),
                title = item.getString("title"),
                author = item.optNullableString("author"),
                lang = item.optNullableString("lang"),
                coverPath = null,
                fileSize = item.getLong("fileSize"),
                addedAt = item.getLong("addedAt"),
                lastOpenedAt = item.optNullableLong("lastOpenedAt"),
                sha256 = item.optNullableString("sha256"),
                sourceFolder = item.optNullableString("sourceFolder"),
                sourceFileName = item.optNullableString("sourceFileName"),
            )
        }
    }

    private fun readBackupJson(): JSONObject {
        val metadata = ZipFile(latestBackup).use { zip ->
            val entries = zip.entries()
            var metadataEntry: ZipEntry? = null
            while (entries.hasMoreElements()) {
                val candidate = entries.nextElement()
                if (!candidate.isDirectory && candidate.name == "data.json") {
                    require(metadataEntry == null) { "ไฟล์สำรองมี data.json ซ้ำ" }
                    metadataEntry = candidate
                }
            }
            val entry = metadataEntry ?: error("ไฟล์สำรองไม่มี data.json")
            require(entry.size >= 0L && entry.size <= MAX_ENTRY_BYTES) {
                "ไฟล์ข้อมูลสำรองมีขนาดใหญ่เกินกำหนดหรือไม่ทราบขนาด"
            }
            zip.getInputStream(entry).use { input ->
                val bytes = input.readBytes()
                require(bytes.size.toLong() == entry.size) { "ข้อมูล data.json ใน ZIP ไม่สมบูรณ์" }
                bytes
            }
        }
        val json = JSONObject(String(metadata, Charsets.UTF_8))
        require(json.optInt("formatVersion", -1) == FORMAT_VERSION) { "เวอร์ชันไฟล์สำรองนี้ไม่รองรับ" }
        return json
    }

    suspend fun restoreLatestBackup(
        sourceMappings: Map<String, RestoreSourceCandidate> = emptyMap(),
    ): PortableBackupSummary = withContext(Dispatchers.IO) {
        requireStorageAccess()
        currentCoroutineContext().ensureActive()
        check(latestBackup.isFile) { "ไม่พบไฟล์สำรอง Kanom" }
        val entries = mutableMapOf<String, ByteArray>()
        // Use the ZIP central directory to enumerate entries, then open streams only for
        // the explicit metadata/cover allowlist. Legacy EPUB entries are never opened,
        // decompressed, copied, or extracted during restore.
        ZipFile(latestBackup).use { zip ->
            val zipEntries = zip.entries()
            var totalRestoredBytes = 0L
            while (zipEntries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = zipEntries.nextElement()
                if (entry.isDirectory || !isRestorablePortableEntry(entry.name)) continue
                require(entry.size >= 0L && entry.size <= MAX_ENTRY_BYTES) {
                    "ไฟล์ในชุดสำรองมีขนาดใหญ่เกินกำหนดหรือไม่ทราบขนาด"
                }
                require(entry.name !in entries) { "ไฟล์สำรองมีรายการซ้ำ: ${entry.name}" }
                totalRestoredBytes += entry.size
                require(totalRestoredBytes <= MAX_TOTAL_RESTORE_BYTES) {
                    "ข้อมูลที่ต้องกู้คืนมีขนาดรวมเกินกำหนด"
                }
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                require(bytes.size.toLong() == entry.size) { "ข้อมูลใน ZIP ไม่สมบูรณ์: ${entry.name}" }
                entries[entry.name] = bytes
            }
        }
        val json = JSONObject(String(entries["data.json"] ?: error("ไฟล์สำรองไม่มี data.json"), Charsets.UTF_8))
        require(json.optInt("formatVersion", -1) == FORMAT_VERSION) { "เวอร์ชันไฟล์สำรองนี้ไม่รองรับ" }

        val bookArray = json.getJSONArray("books")
        val backupBookIds = (0 until bookArray.length())
            .map { bookArray.getJSONObject(it).getString("id") }
            .toSet()
        require(sourceMappings.keys.all { it in backupBookIds }) {
            "มีการจับคู่ไฟล์กับ Book ID ที่ไม่มีอยู่ในชุดสำรอง"
        }
        require(sourceMappings.values.map { it.uri }.distinct().size == sourceMappings.size) {
            "ไม่สามารถใช้ไฟล์ EPUB ต้นฉบับเดียวกันกับหนังสือหลายเล่มได้"
        }
        // Revalidate every selected source immediately before staging the restored rows.
        // This catches changed/replaced files between folder scan and final confirmation.
        sourceMappings.forEach { (bookId, source) ->
            currentCoroutineContext().ensureActive()
            val bookIndex = (0 until bookArray.length()).first { bookArray.getJSONObject(it).getString("id") == bookId }
            val savedHash = bookArray.getJSONObject(bookIndex).optNullableString("sha256")
            val actualHash = context.contentResolver.openInputStream(Uri.parse(source.uri))?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            } ?: error("ไม่สามารถอ่านไฟล์ต้นฉบับที่เลือกได้: ${source.name}")
            require(source.sha256.isNullOrBlank() || actualHash.equals(source.sha256, ignoreCase = true)) {
                "ไฟล์ต้นฉบับเปลี่ยนแปลงหลังจากสแกน: ${source.name}"
            }
            require(savedHash.isNullOrBlank() || actualHash.equals(savedHash, ignoreCase = true)) {
                "SHA-256 ของไฟล์ไม่ตรงกับหนังสือ ${bookArray.getJSONObject(bookIndex).getString("title")}"
            }
        }
        currentCoroutineContext().ensureActive()
        val books = (0 until bookArray.length()).map { i ->
            val o = bookArray.getJSONObject(i)
            val id = o.getString("id")
            val coverEntry = o.optString("coverEntry").takeIf { it.isNotBlank() && it != "null" }
            val coverBytes = coverEntry?.let(entries::get)
            // Ignore legacy portableEpub pointers. Preserve the original URI for explicit relinking.
            val restoredUri = o.getString("uri")
            val coverPath = coverBytes?.let { bytes ->
                val dir = File(context.filesDir, "covers")
                check(dir.exists() || dir.mkdirs()) { "สร้างโฟลเดอร์ภาพปกไม่สำเร็จ" }
                File(dir, "${safeName(id)}.bin").apply { writeBytes(bytes) }.absolutePath
            }
            val source = sourceMappings[id]
            if (source != null) {
                // Validate every chosen SAF URI before the database transaction starts.
                context.contentResolver.openFileDescriptor(Uri.parse(source.uri), "r")?.use { }
                    ?: error("ไม่สามารถเปิดไฟล์ต้นฉบับที่เลือกได้: ${source.name}")
            }
            BookEntity(
                id = id,
                uri = source?.uri ?: restoredUri,
                title = o.getString("title"),
                author = o.optNullableString("author"), lang = o.optNullableString("lang"),
                coverPath = coverPath, fileSize = o.getLong("fileSize"),
                addedAt = o.getLong("addedAt"), lastOpenedAt = o.optNullableLong("lastOpenedAt"),
                sha256 = source?.sha256 ?: o.optNullableString("sha256"),
                sourceFolder = source?.sourceFolder ?: o.optNullableString("sourceFolder"),
                sourceFileName = source?.name ?: o.optNullableString("sourceFileName"),
            )
        }
        val progressArray = json.getJSONArray("progress")
        val progress = (0 until progressArray.length()).map { i ->
            val o = progressArray.getJSONObject(i)
            ProgressEntity(o.getString("bookId"), o.optNullableString("locator"),
                o.optNullableString("ttsLocator"), o.getDouble("percent").toFloat(), o.getLong("updatedAt"))
        }
        val bookmarkArray = json.getJSONArray("bookmarks")
        val bookmarks = (0 until bookmarkArray.length()).map { i ->
            val o = bookmarkArray.getJSONObject(i)
            BookmarkEntity(o.getLong("id"), o.getString("bookId"), o.getString("locator"),
                o.getString("snippet"), o.getLong("createdAt"))
        }
        val ruleArray = json.getJSONArray("replacementRules")
        val rules = (0 until ruleArray.length()).map { i ->
            val o = ruleArray.getJSONObject(i)
            ReplacementRuleEntity(o.getLong("id"), o.getString("scope"), o.optNullableString("bookId"),
                o.getString("pattern"), o.getString("replacement"), o.getBoolean("isRegex"),
                o.getBoolean("enabled"), o.getInt("position"), o.optBoolean("showInReader", false))
        }
        val voiceArray = json.getJSONArray("voiceConfigs")
        val voices = (0 until voiceArray.length()).map { i ->
            val o = voiceArray.getJSONObject(i)
            VoiceConfigEntity(o.getString("lang"), o.getString("providerId"), o.optNullableString("voiceId"),
                o.getDouble("speed").toFloat(), o.getDouble("pitch").toFloat())
        }
        val tagArray = json.optJSONArray("bookTags")
        val tags = (0 until (tagArray?.length() ?: 0)).map { i ->
            val o = tagArray!!.getJSONObject(i)
            BookTagEntity(o.getString("bookId"), o.getString("tag"))
        }
        val noteArray = json.optJSONArray("bookNotes")
        val notes = (0 until (noteArray?.length() ?: 0)).map { i ->
            val o = noteArray!!.getJSONObject(i)
            BookNoteEntity(o.getString("bookId"), o.getString("note"), o.getLong("updatedAt"))
        }
        val sessionArray = json.optJSONArray("readingSessions")
        val sessions = (0 until (sessionArray?.length() ?: 0)).map { i ->
            val o = sessionArray!!.getJSONObject(i)
            ReadingSessionEntity(o.optLong("id", 0L), o.getString("bookId"),
                o.getLong("startedAt"), o.getLong("durationMs"))
        }
        require(tags.all { tag -> books.any { it.id == tag.bookId } }) { "ข้อมูล Tag อ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(notes.all { note -> books.any { it.id == note.bookId } }) { "ข้อมูล Note อ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(sessions.all { session -> books.any { it.id == session.bookId } }) { "ข้อมูล Reading Statistics อ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(progress.all { p -> books.any { it.id == p.bookId } }) { "ข้อมูลตำแหน่งอ่านอ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(bookmarks.all { b -> books.any { it.id == b.bookId } }) { "ข้อมูล Bookmark อ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(rules.filter { it.scope == "book" }.all { r -> r.bookId != null && books.any { it.id == r.bookId } }) {
            "ข้อมูลกฎแทนที่อ้างถึงหนังสือที่ไม่มีในชุดสำรอง"
        }

        val preferencesArray = json.getJSONArray("preferences")
        currentCoroutineContext().ensureActive()
        database.withTransaction {
            // Cancellation at any point before this transaction completes rolls back the
            // existing library and reading state instead of leaving a partially restored DB.
            currentCoroutineContext().ensureActive()
            database.bookTagDao().deleteAll()
            database.bookNoteDao().deleteAll()
            database.readingSessionDao().deleteAll()
            database.bookmarkDao().deleteAll()
            database.progressDao().deleteAll()
            database.replacementRuleDao().deleteAll()
            database.voiceConfigDao().deleteAll()
            database.bookDao().deleteAll()
            database.bookDao().insertAll(books)
            database.progressDao().insertAll(progress)
            database.bookmarkDao().insertAll(bookmarks)
            database.replacementRuleDao().insertAll(rules)
            database.voiceConfigDao().insertAll(voices)
            database.bookTagDao().insertAll(tags)
            database.bookNoteDao().insertAll(notes)
            database.readingSessionDao().insertAll(sessions)
            // Apply preferences before the Room transaction commits. A DataStore failure
            // therefore aborts the DB restore rather than leaving DB and settings split.
            currentCoroutineContext().ensureActive()
            restorePreferences(preferencesArray)
            currentCoroutineContext().ensureActive()
        }
        PortableBackupSummary(books.size, progress.size, bookmarks.size, rules.size, voices.size, tags.size, notes.size, sessions.size, latestBackup.absolutePath)
    }

    private fun requireStorageAccess() {
        check(hasStorageAccess()) {
            "Kanom ยังไม่ได้รับสิทธิ์เข้าถึงไฟล์ทั้งหมด กรุณาอนุญาตในหน้าตั้งค่าของ Android ก่อน"
        }
    }

    private fun encodePreferences(prefs: Preferences): JSONArray = JSONArray().apply {
        prefs.asMap().filterNot { it.key.name == "premium_unlocked" }.forEach { (key, value) ->
            val item = JSONObject().put("name", key.name)
            when (value) {
                is Boolean -> item.put("type", "boolean").put("value", value)
                is Int -> item.put("type", "int").put("value", value)
                is Long -> item.put("type", "long").put("value", value)
                is Float -> item.put("type", "float").put("value", value.toDouble())
                is Double -> item.put("type", "double").put("value", value)
                is String -> item.put("type", "string").put("value", value)
                is Set<*> -> item.put("type", "stringSet").put("value", JSONArray(value.map { it.toString() }))
            }
            put(item)
        }
    }

    private suspend fun restorePreferences(array: JSONArray) {
        dataStore.edit { prefs ->
            // This entitlement is intentionally excluded from portable backups. Preserve the
            // value already belonging to this installation instead of clearing it on restore.
            val localPremiumEntitlement = prefs[booleanPreferencesKey("premium_unlocked")]
            prefs.clear()
            if (localPremiumEntitlement != null) {
                prefs[booleanPreferencesKey("premium_unlocked")] = localPremiumEntitlement
            }
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.getString("name")
                // Portable backups never grant or revoke installation-local entitlements.
                if (name == "premium_unlocked") continue
                val value = item.get("value")
                when (item.getString("type")) {
                    "boolean" -> prefs[booleanPreferencesKey(name)] = value as Boolean
                    "int" -> prefs[intPreferencesKey(name)] = (value as Number).toInt()
                    "long" -> prefs[longPreferencesKey(name)] = (value as Number).toLong()
                    "float" -> prefs[floatPreferencesKey(name)] = (value as Number).toFloat()
                    "double" -> prefs[doublePreferencesKey(name)] = (value as Number).toDouble()
                    "string" -> prefs[stringPreferencesKey(name)] = value as String
                    "stringSet" -> {
                        val values = item.getJSONArray("value")
                        prefs[stringSetPreferencesKey(name)] = (0 until values.length()).map { values.getString(it) }.toSet()
                    }
                }
            }
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeUnless { it == "null" }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun safeName(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(100)

    companion object {
        private const val FORMAT_VERSION = 1
        private const val MAX_ENTRY_BYTES = 512 * 1024 * 1024
        private const val MAX_TOTAL_RESTORE_BYTES = 768L * 1024L * 1024L
    }
}
