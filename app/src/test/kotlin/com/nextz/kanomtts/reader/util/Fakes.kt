package com.nextz.kanomtts.reader.util

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.db.ReplacementRuleDao
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.data.repository.BookmarkStore
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import com.nextz.kanomtts.reader.ReaderBookSource
import com.nextz.kanomtts.text.replacement.ReplacementRule
import com.nextz.kanomtts.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.readium.r2.shared.publication.Publication

class FakeReaderPreferences(
    initial: KanomPreferences = KanomPreferences(),
) : ReaderPreferences {
    private val _prefs = MutableStateFlow(initial)
    override val preferences: Flow<KanomPreferences> = _prefs
    override suspend fun setReaderTheme(v: ReaderTheme) { _prefs.value = _prefs.value.copy(readerTheme = v) }
    override suspend fun setReadingFont(v: ReadingFont) { _prefs.value = _prefs.value.copy(readingFont = v) }
    override suspend fun setFontSizeSp(v: Int) { _prefs.value = _prefs.value.copy(fontSizeSp = v) }
    override suspend fun setProgressFontSizeSp(v: Int) { _prefs.value = _prefs.value.copy(progressFontSizeSp = v) }
    override suspend fun setTopProgressEnabled(v: Boolean) { _prefs.value = _prefs.value.copy(topProgressEnabled = v) }
    override suspend fun setFooterProgressEnabled(v: Boolean) { _prefs.value = _prefs.value.copy(footerProgressEnabled = v) }
    override suspend fun setTopProgressLayout(left: String, center: String, right: String) { _prefs.value = _prefs.value.copy(topProgressLeft = left, topProgressCenter = center, topProgressRight = right) }
    override suspend fun setFooterProgressLayout(left: String, center: String, right: String) { _prefs.value = _prefs.value.copy(footerProgressLeft = left, footerProgressCenter = center, footerProgressRight = right) }
    override suspend fun setLineHeight(v: Float) { _prefs.value = _prefs.value.copy(lineHeight = v) }
    override suspend fun setParagraphIndentEm(v: Float) { _prefs.value = _prefs.value.copy(paragraphIndentEm = v) }
    override suspend fun setParagraphSpacingEm(v: Float) { _prefs.value = _prefs.value.copy(paragraphSpacingEm = v) }
    override suspend fun setMarginDp(v: Int) { _prefs.value = _prefs.value.copy(marginDp = v) }
    override suspend fun setTopMarginDp(v: Int) { _prefs.value = _prefs.value.copy(topMarginDp = v) }
    override suspend fun setBottomMarginDp(v: Int) { _prefs.value = _prefs.value.copy(bottomMarginDp = v) }
    override suspend fun setBoldText(v: Boolean) { _prefs.value = _prefs.value.copy(boldText = v) }
    override suspend fun setReverseVolumeKeys(v: Boolean) { _prefs.value = _prefs.value.copy(reverseVolumeKeys = v) }
    // Task 6 escape hatches
    override suspend fun setUsePublisherStyles(v: Boolean) { _prefs.value = _prefs.value.copy(usePublisherStyles = v) }
    override suspend fun setForceFont(v: Boolean) { _prefs.value = _prefs.value.copy(forceFont = v) }
    override suspend fun setStripColors(v: Boolean) { _prefs.value = _prefs.value.copy(stripColors = v) }
    override suspend fun setThaiWordBreak(v: Boolean) { _prefs.value = _prefs.value.copy(thaiWordBreak = v) }
    override suspend fun setPageAnimation(v: Boolean) { _prefs.value = _prefs.value.copy(pageAnimation = v) }
    override suspend fun setVisualReplacement(v: Boolean) { _prefs.value = _prefs.value.copy(visualReplacement = v) }
    override suspend fun setReadingUnit(v: ReadingUnit) { _prefs.value = _prefs.value.copy(readingUnit = v) }
    override suspend fun setSkipUrls(v: Boolean) { _prefs.value = _prefs.value.copy(skipUrls = v) }
    override suspend fun setHighlightOn(v: Boolean) { _prefs.value = _prefs.value.copy(highlightOn = v) }
    override suspend fun setAutoScroll(v: Boolean) { _prefs.value = _prefs.value.copy(autoScroll = v) }
    override suspend fun setSleepTimerDefaultMin(v: Int) { _prefs.value = _prefs.value.copy(sleepTimerDefaultMin = v) }
}

class FakeBookmarkStore : BookmarkStore {
    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    private var nextId = 1L

    override fun observe(bookId: String): Flow<List<BookmarkEntity>> =
        _bookmarks.map { list -> list.filter { it.bookId == bookId } }

    override suspend fun add(bookmark: BookmarkEntity): Long {
        val id = nextId++
        _bookmarks.value = _bookmarks.value + bookmark.copy(id = id)
        return id
    }

    override suspend fun remove(id: Long) {
        _bookmarks.value = _bookmarks.value.filter { it.id != id }
    }

    override suspend fun updateSnippet(id: Long, snippet: String) {
        _bookmarks.value = _bookmarks.value.map { if (it.id == id) it.copy(snippet = snippet) else it }
    }
}

/**
 * In-memory fake DAO backing all observables with a single [MutableStateFlow], so reads reflect
 * writes (mirrors FakeBookmarkStore). The observe* projections match the real DAO's query semantics
 * (scope filter + position ordering).
 */
private class FakeReplacementRuleDao : ReplacementRuleDao {
    private val state = MutableStateFlow<List<ReplacementRuleEntity>>(emptyList())

    override suspend fun insert(rule: ReplacementRuleEntity): Long {
        val id = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + rule.copy(id = id)
        return id
    }
    override suspend fun insertAll(rules: List<ReplacementRuleEntity>) {
        val existing = state.value.associateBy { it.id }.toMutableMap()
        rules.forEach { rule -> existing[rule.id] = rule }
        state.value = existing.values.toList()
    }
    override suspend fun getAllForBackup(): List<ReplacementRuleEntity> = state.value.toList()
    override suspend fun deleteAll() {
        state.value = emptyList()
    }
    override suspend fun update(rule: ReplacementRuleEntity) {
        state.value = state.value.map { if (it.id == rule.id) rule else it }
    }
    override suspend fun delete(id: Long) {
        state.value = state.value.filter { it.id != id }
    }
    override suspend fun deleteByBook(bookId: String) {
        state.value = state.value.filter { !(it.scope == "book" && it.bookId == bookId) }
    }
    override suspend fun updateAll(rules: List<ReplacementRuleEntity>) {
        val byId = rules.associateBy { it.id }
        state.value = state.value.map { byId[it.id] ?: it }
    }

    override suspend fun maxPosition(): Int = state.value.maxOfOrNull { it.position } ?: -1

    override fun observeEffective(bookId: String): Flow<List<ReplacementRuleEntity>> =
        state.map { list ->
            list.filter { it.scope == "global" || (it.scope == "book" && it.bookId == bookId) }
                .sortedBy { it.position }
        }
    override fun observeGlobal(): Flow<List<ReplacementRuleEntity>> =
        state.map { list -> list.filter { it.scope == "global" }.sortedBy { it.position } }
    override fun observeByBook(bookId: String): Flow<List<ReplacementRuleEntity>> =
        state.map { list ->
            list.filter { it.scope == "book" && it.bookId == bookId }.sortedBy { it.position }
        }
    override fun globalCount(): Flow<Int> =
        state.map { list -> list.count { it.scope == "global" } }
    override fun observeEffectiveVisual(bookId: String): Flow<List<ReplacementRuleEntity>> =
        state.map { list ->
            list.filter { it.showInReader && (it.scope == "global" || (it.scope == "book" && it.bookId == bookId)) }
                .sortedBy { it.position }
        }
}

fun fakeReplacementRuleRepository(): ReplacementRuleRepository =
    ReplacementRuleRepository(FakeReplacementRuleDao())

class FakeReaderBookSource : ReaderBookSource {
    var book: BookEntity? = null
    var publication: Publication? = null
    var storedProgress: ProgressEntity? = null

    var saveCount: Int = 0
    var lastSavedProgress: ProgressEntity? = null
    var touchedBook: BookEntity? = null

    // Partial-column save tracking (I2)
    var readingLocatorSaveCount: Int = 0
    var lastReadingLocator: String? = null

    override suspend fun book(bookId: String): BookEntity? = book
    override suspend fun open(uri: String): Publication? = publication
    /** Returns the fake publication regardless of transform flags (rendering tested on device). */
    override suspend fun openForDisplay(uri: String, thaiWordBreak: Boolean, visualRules: List<ReplacementRule>?): Publication? = publication
    override suspend fun progress(bookId: String): ProgressEntity? = storedProgress
    override suspend fun saveProgress(progress: ProgressEntity) {
        saveCount++
        lastSavedProgress = progress
    }
    override suspend fun saveReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long) {
        readingLocatorSaveCount++
        lastReadingLocator = locator
    }
    override suspend fun touchOpened(book: BookEntity) { touchedBook = book }
}
