package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val author: String?,
    val lang: String?,
    val coverPath: String?,
    val fileSize: Long,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val sha256: String? = null,
    val sourceFolder: String? = null,
    val sourceFileName: String? = null,
)
