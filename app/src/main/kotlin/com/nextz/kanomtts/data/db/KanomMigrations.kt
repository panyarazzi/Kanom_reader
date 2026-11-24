package com.nextz.kanomtts.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE replacement_rules ADD COLUMN showInReader INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reading_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "bookId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationMs INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN sha256 TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN sourceFolder TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS book_tags (" +
                "bookId TEXT NOT NULL, tag TEXT NOT NULL, " +
                "PRIMARY KEY(bookId, tag), " +
                "FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_book_tags_bookId ON book_tags(bookId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS book_notes (" +
                "bookId TEXT NOT NULL PRIMARY KEY, note TEXT NOT NULL, updatedAt INTEGER NOT NULL, " +
                "FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN sourceFileName TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN seriesName TEXT")
        db.execSQL("ALTER TABLE books ADD COLUMN seriesPosition REAL")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS book_tag_library (tag TEXT NOT NULL PRIMARY KEY)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN readingStatus TEXT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
    }
}
