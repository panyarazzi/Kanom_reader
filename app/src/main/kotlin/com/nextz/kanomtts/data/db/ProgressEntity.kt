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
