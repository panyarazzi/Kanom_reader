package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,          // stable id (e.g. content uri hash or uuid)
    val uri: String,
    val title: String,
    val author: String?,
    val lang: String?,                   // BCP-47 primary language hint
    val coverPath: String?,
    val fileSize: Long,
    val addedAt: Long,                   // epoch millis
    val lastOpenedAt: Long?,
)
