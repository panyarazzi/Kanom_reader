package com.nextz.kanomtts.data.db
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
@Entity(
    tableName = "book_notes",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class BookNoteEntity(@PrimaryKey val bookId: String, val note: String, val updatedAt: Long)
