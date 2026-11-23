package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Saved reusable tags are independent of book assignments. Removing one from this
// table never removes matching rows from book_tags.
@Entity(tableName = "book_tag_library")
data class BookTagLibraryEntity(
    @PrimaryKey val tag: String,
)
