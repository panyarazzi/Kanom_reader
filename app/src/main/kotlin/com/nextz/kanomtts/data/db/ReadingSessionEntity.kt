package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A short, persisted segment of foreground reading time.
 * Segments are written only while the reader is visible and recently interacted with.
 */
@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startedAt: Long,
    val durationMs: Long,
)
