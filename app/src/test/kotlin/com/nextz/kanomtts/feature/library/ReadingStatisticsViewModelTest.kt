package com.nextz.kanomtts.feature.library

import com.nextz.kanomtts.data.db.ReadingSessionEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatisticsViewModelTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    private fun session(date: LocalDate, durationMs: Long) = ReadingSessionEntity(
        bookId = "book",
        startedAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        durationMs = durationMs,
    )

    @Test
    fun allTimeChartStartsAtTheOldestRecordedSession() {
        val sessions = listOf(
            session(today.minusDays(400), 120_000L),
            session(today.minusDays(20), 60_000L),
            session(today.minusDays(1), 30_000L),
        )

        val all = buildStatistics(emptyList(), sessions, StatisticsRange.ALL)
        val month = buildStatistics(emptyList(), sessions, StatisticsRange.MONTH)
        val week = buildStatistics(emptyList(), sessions, StatisticsRange.WEEK)

        assertEquals(today.minusDays(400), all.chartDays.first().date)
        assertEquals(401, all.chartDays.size)
        assertEquals(120_000L, all.chartDays.first().durationMs)
        assertEquals(today.minusDays(29), month.chartDays.first().date)
        assertEquals(30, month.chartDays.size)
        assertEquals(today.minusDays(6), week.chartDays.first().date)
        assertEquals(7, week.chartDays.size)
    }

    @Test
    fun allTimeChartIncludesZeroDurationDaysBetweenSessions() {
        val all = buildStatistics(
            emptyList(),
            listOf(session(today.minusDays(2), 60_000L), session(today, 30_000L)),
            StatisticsRange.ALL,
        )

        assertEquals(3, all.chartDays.size)
        assertEquals(0L, all.chartDays[1].durationMs)
        assertTrue(all.chartDays.last().durationMs > 0L)
    }
}
