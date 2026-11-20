package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** scope: "global" or "book"; bookId non-null only when scope == "book". */
@Entity(tableName = "replacement_rules", indices = [Index("scope"), Index("bookId")])
data class ReplacementRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val bookId: String?,
    val pattern: String,
    val replacement: String,
    val isRegex: Boolean,
    val enabled: Boolean,
    val position: Int,                   // ordering ("order" is SQL-reserved)
    val showInReader: Boolean = false,
)
