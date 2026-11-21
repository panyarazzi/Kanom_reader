package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A text-range annotation. The original Readium Locator is preserved intact;
 * selectedText and context are clean metadata and never replace the Locator.
 */
@Entity(tableName = "highlights", indices = [Index("bookId")])
data class ReaderHighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val locator: String,
    val selectedText: String,
    val beforeText: String? = null,
    val afterText: String? = null,
    val createdAt: Long,
)
