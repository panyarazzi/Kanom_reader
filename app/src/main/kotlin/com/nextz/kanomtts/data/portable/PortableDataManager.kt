package com.nextz.kanomtts.data.portable

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.net.Uri
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
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class PortableBackupSummary(
    val books: Int,
    val progress: Int,
    val bookmarks: Int,
    val replacementRules: Int,
    val voiceConfigs: Int,
    val epubCopies: Int,
    val epubCopyFailures: Int,
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
    )

    fun hasStorageAccess(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        else -> ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun portableFolderPath(): String = root.absolutePath

    fun ensurePortableFolders(): Boolean {
        if (!hasStorageAccess()) return false
        return listOf(root, File(root, "books"), File(root, "settings"), File(root, "progress"),
            File(root, "bookmarks"), File(root, "covers"), backupDir).all { it.exists() || it.mkdirs() }
    }

    fun latestBackupExists(): Boolean = hasStorageAccess() && latestBackup.isFile

    suspend fun createBackup(includeEpubFiles: Boolean = false): PortableBackupSummary = withContext(Dispatchers.IO) {
        requireStorageAccess()
        check(ensurePortableFolders()) { "สร้างโฟลเดอร์ Kanom ไม่สำเร็จ" }
        val snapshot = database.withTransaction {
            DbSnapshot(
                books = database.bookDao().getAllForBackup(),
                progress = database.progressDao().getAllForBackup(),
                bookmarks = database.bookmarkDao().getAllForBackup(),
                rules = database.replacementRuleDao().getAllForBackup(),
                voices = database.voiceConfigDao().getAllForBackup(),
            )
        }
        val books = snapshot.books
        val progress = snapshot.progress
        val bookmarks = snapshot.bookmarks
        val rules = snapshot.rules
        val voices = snapshot.voices
        val prefs = dataStore.data.first()
        val portableEpubs = mutableMapOf<String, File>()
        if (includeEpubFiles) {
            val booksDir = File(root, "books")
            books.forEach { book ->
                val target = File(booksDir, "${safeName(book.id)}.epub")
                val tempBook = File(booksDir, "${safeName(book.id)}.epub.tmp")
                val copied = runCatching {
                    val input = context.contentResolver.openInputStream(Uri.parse(book.uri))
                        ?: error("ไม่สามารถอ่านไฟล์ต้นฉบับ")
                    input.use { source -> tempBook.outputStream().buffered().use { source.copyTo(it) } }
                    check(tempBook.renameTo(target) || run { target.delete(); tempBook.renameTo(target) })
                    target
                }.getOrNull()
                if (copied != null) portableEpubs[book.id] = copied else tempBook.delete()
            }
        }
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
                        putNullable("portableEpub", if (portableEpubs.containsKey(book.id)) "books/${safeName(book.id)}.epub" else null)
                        put("fileSize", book.fileSize)
                        put("addedAt", book.addedAt); putNullable("lastOpenedAt", book.lastOpenedAt)
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

        val dated = File(backupDir, "kanom-${System.currentTimeMillis()}.zip")
        latestBackup.copyTo(dated, overwrite = true)
        File(root, "manifest.json").writeText(
            JSONObject().put("formatVersion", FORMAT_VERSION)
                .put("lastBackupAt", System.currentTimeMillis())
                .put("latestBackup", "backup/kanom-latest.zip").toString(2),
            Charsets.UTF_8,
        )
        PortableBackupSummary(books.size, progress.size, bookmarks.size, rules.size, voices.size, portableEpubs.size,
            if (includeEpubFiles) books.size - portableEpubs.size else 0, latestBackup.absolutePath)
    }

    suspend fun restoreLatestBackup(): PortableBackupSummary = withContext(Dispatchers.IO) {
        requireStorageAccess()
        check(latestBackup.isFile) { "ไม่พบไฟล์สำรอง Kanom" }
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(FileInputStream(latestBackup).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    require(entry.name == "data.json" || entry.name.startsWith("covers/")) {
                        "ไฟล์สำรองมีรายการที่ไม่รองรับ"
                    }
                    val bytes = zip.readBytes()
                    require(bytes.size <= MAX_ENTRY_BYTES) { "ไฟล์ในชุดสำรองมีขนาดใหญ่เกินกำหนด" }
                    entries[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        val json = JSONObject(String(entries["data.json"] ?: error("ไฟล์สำรองไม่มี data.json"), Charsets.UTF_8))
        require(json.optInt("formatVersion", -1) == FORMAT_VERSION) { "เวอร์ชันไฟล์สำรองนี้ไม่รองรับ" }

        val bookArray = json.getJSONArray("books")
        val books = (0 until bookArray.length()).map { i ->
            val o = bookArray.getJSONObject(i)
            val id = o.getString("id")
            val coverEntry = o.optString("coverEntry").takeIf { it.isNotBlank() && it != "null" }
            val coverBytes = coverEntry?.let(entries::get)
            val portableEpubName = o.optNullableString("portableEpub")
            val portableEpub = portableEpubName?.let { name ->
                if (name.startsWith("books/") && name.substringAfter("books/").contains('/') .not()) {
                    File(root, name).takeIf { it.isFile }
                } else null
            }
            val restoredUri = portableEpub?.let { Uri.fromFile(it).toString() } ?: o.getString("uri")
            val coverPath = coverBytes?.let { bytes ->
                val dir = File(context.filesDir, "covers")
                check(dir.exists() || dir.mkdirs()) { "สร้างโฟลเดอร์ภาพปกไม่สำเร็จ" }
                File(dir, "${safeName(id)}.bin").apply { writeBytes(bytes) }.absolutePath
            }
            BookEntity(
                id = id, uri = restoredUri, title = o.getString("title"),
                author = o.optNullableString("author"), lang = o.optNullableString("lang"),
                coverPath = coverPath, fileSize = o.getLong("fileSize"),
                addedAt = o.getLong("addedAt"), lastOpenedAt = o.optNullableLong("lastOpenedAt"),
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
        require(progress.all { p -> books.any { it.id == p.bookId } }) { "ข้อมูลตำแหน่งอ่านอ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(bookmarks.all { b -> books.any { it.id == b.bookId } }) { "ข้อมูล Bookmark อ้างถึงหนังสือที่ไม่มีในชุดสำรอง" }
        require(rules.filter { it.scope == "book" }.all { r -> r.bookId != null && books.any { it.id == r.bookId } }) {
            "ข้อมูลกฎแทนที่อ้างถึงหนังสือที่ไม่มีในชุดสำรอง"
        }

        database.withTransaction {
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
        }
        restorePreferences(json.getJSONArray("preferences"))
        val portableCount = books.count { it.uri.startsWith("file:") && it.uri.contains("/Kanom/books/") }
        PortableBackupSummary(books.size, progress.size, bookmarks.size, rules.size, voices.size,
            portableCount, 0, latestBackup.absolutePath)
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
            prefs.clear()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.getString("name")
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
    }
}
