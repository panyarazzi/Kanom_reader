package com.nextz.kanomtts.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookNoteDao
import com.nextz.kanomtts.data.db.BookNoteEntity
import com.nextz.kanomtts.data.db.BookTagDao
import com.nextz.kanomtts.data.db.BookTagLibraryDao
import com.nextz.kanomtts.data.db.BookTagLibraryEntity
import com.nextz.kanomtts.data.db.BookTagEntity
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.repository.BookReader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.room.withTransaction

data class BookAnnotationRow(
    val id: String,
    val title: String,
    val author: String?,
    val sourceFileName: String?,
    val sourceFolder: String?,
    val tags: List<String>,
    val note: String,
)

data class BookAnnotationsUiState(
    val books: List<BookAnnotationRow> = emptyList(),
    val allBooks: List<BookAnnotationRow> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val savedTags: List<String> = emptyList(),
    val query: String = "",
    val selectedTag: String? = null,
    val notesOnly: Boolean = false,
)

data class AnnotationSaveUiState(
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BookAnnotationsViewModel @Inject constructor(
    books: BookReader,
    private val database: KanomDatabase,
    private val tagLibraryDao: BookTagLibraryDao,
) : ViewModel() {
    private val tagDao: BookTagDao get() = database.bookTagDao()
    private val noteDao: BookNoteDao get() = database.bookNoteDao()
    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val notesOnly = MutableStateFlow(false)
    private val _saveState = MutableStateFlow(AnnotationSaveUiState())
    val saveState: StateFlow<AnnotationSaveUiState> = _saveState

    private val allRows = combine(
        books.observeLibrary(),
        tagDao.observeAll(),
        noteDao.observeAll(),
    ) { allBooks, allTags, allNotes ->
        val tagsByBook = allTags.groupBy { it.bookId }
            .mapValues { (_, values) -> values.map { it.tag }.sorted() }
        val notesByBook = allNotes.associate { it.bookId to it.note }
        allBooks.map { book ->
            BookAnnotationRow(
                book.id, book.title, book.author, book.sourceFileName, book.sourceFolder,
                tagsByBook[book.id].orEmpty(), notesByBook[book.id].orEmpty(),
            )
        }.sortedWith(compareBy<BookAnnotationRow>({ it.sourceFolder.orEmpty().lowercase() }, { it.title.lowercase() }))
    }

    val uiState: StateFlow<BookAnnotationsUiState> = combine(
        allRows, query, selectedTag, notesOnly,
    ) { rows, search, tag, onlyNotes ->
        BookAnnotationsUiState(
            books = filterBookAnnotationRows(rows, search, tag, onlyNotes),
            allBooks = rows,
            availableTags = rows.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            query = search,
            selectedTag = tag,
            notesOnly = onlyNotes,
        )
    }.combine(tagLibraryDao.observeAll()) { state, savedTags ->
        state.copy(savedTags = savedTags.map { it.tag })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookAnnotationsUiState())

    fun setQuery(value: String) { query.value = value }
    fun setTagFilter(value: String?) { selectedTag.value = if (selectedTag.value == value) null else value }
    fun setNotesOnly(value: Boolean) { notesOnly.value = value }

    fun saveReusableTag(value: String) {
        val tag = value.trim().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch { tagLibraryDao.insertIfMissing(tag) }
    }

    fun deleteReusableTag(value: String) {
        viewModelScope.launch { tagLibraryDao.delete(value) }
    }

    fun clearSaveError() {
        _saveState.value = AnnotationSaveUiState()
    }

    fun save(bookId: String, tagsText: String, noteText: String, onSaved: () -> Unit) {
        if (_saveState.value.saving) return
        _saveState.value = AnnotationSaveUiState(saving = true)
        viewModelScope.launch {
            try {
                val tags = tagsText.split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .map { BookTagEntity(bookId, it) }
                database.withTransaction {
                    tagDao.deleteForBook(bookId)
                    if (tags.isNotEmpty()) tagDao.insertAll(tags)
                    val note = noteText.trim()
                    if (note.isEmpty()) noteDao.deleteForBook(bookId)
                    else noteDao.upsert(BookNoteEntity(bookId, note, System.currentTimeMillis()))
                }
                _saveState.value = AnnotationSaveUiState()
                onSaved()
            } catch (cancelled: CancellationException) {
                _saveState.value = AnnotationSaveUiState()
                throw cancelled
            } catch (failure: Exception) {
                _saveState.value = AnnotationSaveUiState(
                    error = "บันทึก Tags / Notes ไม่สำเร็จ: ${failure.message ?: "เกิดข้อผิดพลาด"}",
                )
            }
        }
    }

    // Export the complete library, not just the currently filtered subset.
    fun exportCsv(): String = buildBookReviewCsv(uiState.value.allBooks)
}

internal fun filterBookAnnotationRows(
    rows: List<BookAnnotationRow>,
    query: String,
    selectedTag: String?,
    notesOnly: Boolean,
): List<BookAnnotationRow> {
    val needle = query.trim()
    return rows.filter { row ->
        (selectedTag == null || row.tags.any { it.equals(selectedTag, ignoreCase = true) }) &&
            (!notesOnly || row.note.isNotBlank()) &&
            (needle.isBlank() ||
                row.title.contains(needle, ignoreCase = true) ||
                row.author.orEmpty().contains(needle, ignoreCase = true) ||
                row.sourceFileName.orEmpty().contains(needle, ignoreCase = true) ||
                row.tags.any { it.contains(needle, ignoreCase = true) } ||
                row.note.contains(needle, ignoreCase = true))
    }
}

internal fun buildBookReviewCsv(rows: List<BookAnnotationRow>): String {
    fun csv(value: String?): String = "\"" + (value ?: "").replace("\"", "\"\"") + "\""
    return buildString {
        appendLine("title,author,filename,folder,tags,note")
        rows.forEach { row ->
            appendLine(listOf(csv(row.title), csv(row.author), csv(row.sourceFileName), csv(row.sourceFolder),
                csv(row.tags.joinToString(" | ")), csv(row.note)).joinToString(","))
        }
    }
}
