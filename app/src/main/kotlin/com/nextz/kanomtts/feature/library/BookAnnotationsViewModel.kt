package com.nextz.kanomtts.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookNoteDao
import com.nextz.kanomtts.data.db.BookNoteEntity
import com.nextz.kanomtts.data.db.BookTagDao
import com.nextz.kanomtts.data.db.BookTagEntity
import com.nextz.kanomtts.data.repository.BookReader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookAnnotationRow(
    val id: String,
    val title: String,
    val author: String?,
    val sourceFileName: String?,
    val sourceFolder: String?,
    val tags: List<String>,
    val note: String,
)

data class BookAnnotationsUiState(val books: List<BookAnnotationRow> = emptyList())

@HiltViewModel
class BookAnnotationsViewModel @Inject constructor(
    books: BookReader,
    private val tagDao: BookTagDao,
    private val noteDao: BookNoteDao,
) : ViewModel() {
    val uiState: StateFlow<BookAnnotationsUiState> = combine(
        books.observeLibrary(),
        tagDao.observeAll(),
        noteDao.observeAll(),
    ) { allBooks, allTags, allNotes ->
        val tagsByBook = allTags.groupBy { it.bookId }.mapValues { (_, values) -> values.map { it.tag }.sorted() }
        val notesByBook = allNotes.associate { it.bookId to it.note }
        BookAnnotationsUiState(
            books = allBooks.sortedBy { it.title.lowercase() }.map { book ->
                BookAnnotationRow(book.id, book.title, book.author, book.sourceFileName, book.sourceFolder,
                    tagsByBook[book.id].orEmpty(), notesByBook[book.id].orEmpty())
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookAnnotationsUiState())

    fun save(bookId: String, tagsText: String, noteText: String) {
        viewModelScope.launch {
            val tags = tagsText.split(',', ';', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .map { BookTagEntity(bookId, it) }
            tagDao.deleteForBook(bookId)
            if (tags.isNotEmpty()) tagDao.insertAll(tags)
            val note = noteText.trim()
            if (note.isEmpty()) noteDao.deleteForBook(bookId)
            else noteDao.upsert(BookNoteEntity(bookId, note, System.currentTimeMillis()))
        }
    }

    fun exportCsv(): String = buildBookReviewCsv(uiState.value.books)
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
