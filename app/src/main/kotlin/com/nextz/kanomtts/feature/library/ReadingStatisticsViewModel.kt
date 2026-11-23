package com.nextz.kanomtts.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.BookTagDao
import com.nextz.kanomtts.data.db.BookNoteDao
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.db.ReadingSessionDao
import com.nextz.kanomtts.data.db.ReadingSessionEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.ProgressReader
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StatisticsRange(val label: String, val days: Int?) {
    WEEK("7 วัน", 7),
    MONTH("30 วัน", 30),
    ALL("ทั้งหมด", null),
}

data class ReadingDay(
    val date: LocalDate,
    val durationMs: Long,
)

data class ReadingStatisticsUiState(
    val todayReadingMs: Long = 0,
    val daysReadThisWeek: Int = 0,
    val weekReadingMs: Long = 0,
    val latestBook: BookCard? = null,
    val mostProgressedBook: BookCard? = null,
    val currentlyReading: List<BookCard> = emptyList(),
    val completedBooks: List<BookCard> = emptyList(),
    val completedCount: Int = 0,
    val chartDays: List<ReadingDay> = emptyList(),
    val range: StatisticsRange = StatisticsRange.WEEK,
    val tagCounts: Map<String, Int> = emptyMap(),
    val noteCount: Int = 0,
    val notePreviews: List<Pair<String, String>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingStatisticsViewModel @Inject constructor(
    bookReader: BookReader,
    progressReader: ProgressReader,
    sessionDao: ReadingSessionDao,
    tagDao: BookTagDao,
    noteDao: BookNoteDao,
) : ViewModel() {
    private val selectedRange = MutableStateFlow(StatisticsRange.WEEK)

    private val booksWithProgress = bookReader.observeLibrary().flatMapLatest { books ->
        if (books.isEmpty()) {
            flowOf(emptyList())
        } else {
            val streams = books.map { book ->
                combine(flowOf(book), progressReader.observe(book.id)) { b, p -> b to p }
            }
            combine(streams) { values -> values.toList() }
        }
    }

    private val baseStatistics = combine(
        booksWithProgress,
        sessionDao.observeAll(),
        selectedRange,
    ) { pairs, sessions, range ->
        Triple(pairs, sessions, range)
    }

    val uiState: StateFlow<ReadingStatisticsUiState> = combine(
        baseStatistics,
        tagDao.observeAll(),
        noteDao.observeAll(),
    ) { base, tags, notes ->
        buildStatistics(base.first, base.second, base.third).copy(
            tagCounts = tags.groupingBy { it.tag }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER),
            noteCount = notes.count { it.note.isNotBlank() },
            notePreviews = notes.sortedByDescending { it.updatedAt }.take(3).mapNotNull { note ->
                base.first.firstOrNull { it.first.id == note.bookId }?.first?.title?.let { title -> title to note.note }
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReadingStatisticsUiState(),
    )

    fun setRange(range: StatisticsRange) {
        selectedRange.value = range
    }
}

internal fun buildStatistics(
    pairs: List<Pair<BookEntity, ProgressEntity?>>,
    sessions: List<ReadingSessionEntity>,
    range: StatisticsRange,
): ReadingStatisticsUiState {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfWeek = today.with(DayOfWeek.MONDAY)
    val durationsByDate = sessions
        .filter { it.durationMs > 0 }
        .groupBy { java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .mapValues { (_, values) -> values.sumOf { it.durationMs } }

    fun toCard(book: BookEntity, progress: ProgressEntity?): BookCard {
        val percent = (progress?.percent ?: 0f).coerceIn(0f, 1f)
        return BookCard(
            id = book.id,
            title = book.title,
            author = book.author,
            coverPath = book.coverPath,
            percent = percent,
            isReading = percent > 0f && percent < 0.999f,
        )
    }

    val cards = pairs.map { (book, progress) -> Triple(book, progress, toCard(book, progress)) }
    val latest = cards.filter { it.first.lastOpenedAt != null }
        .maxByOrNull { it.first.lastOpenedAt ?: 0L }?.third
    val completed = cards.filter { (book, progress, _) ->
        (progress?.percent ?: 0f) >= 0.999f
    }.sortedByDescending { (_, progress, _) -> progress?.updatedAt ?: 0L }.map { it.third }
    val reading = cards.filter { (_, progress, _) ->
        val value = progress?.percent ?: 0f
        value > 0f && value < 0.999f
    }.sortedByDescending { (book, _, _) -> book.lastOpenedAt ?: 0L }.map { it.third }
    val mostProgressed = reading.maxByOrNull { it.percent }

    val weekDays = (0L..6L).map { offset ->
        val date = startOfWeek.plusDays(offset)
        ReadingDay(date, durationsByDate[date] ?: 0L)
    }
    val chartStart = when (range) {
        StatisticsRange.WEEK -> today.minusDays(6)
        StatisticsRange.MONTH -> today.minusDays(29)
        StatisticsRange.ALL -> durationsByDate.keys.minOrNull() ?: today.minusDays(6)
    }
    val chartDates = if (chartStart > today) {
        listOf(today)
    } else {
        generateSequence(chartStart) { date ->
            if (date < today) date.plusDays(1) else null
        }.toList()
    }
    val chartDays = chartDates.map { date ->
        ReadingDay(date, durationsByDate[date] ?: 0L)
    }

    return ReadingStatisticsUiState(
        todayReadingMs = durationsByDate[today] ?: 0L,
        daysReadThisWeek = weekDays.count { it.durationMs > 0 },
        weekReadingMs = weekDays.sumOf { it.durationMs },
        latestBook = latest,
        mostProgressedBook = mostProgressed,
        currentlyReading = reading,
        completedBooks = completed,
        completedCount = completed.size,
        chartDays = chartDays,
        range = range,
    )
}

internal fun formatReadingDuration(durationMs: Long): String {
    val minutes = (durationMs.coerceAtLeast(0) / 60_000L)
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours} ชม. ${remainder} นาที" else "${remainder} นาที"
}

internal fun formatReadingDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d/M", Locale("th", "TH")))
