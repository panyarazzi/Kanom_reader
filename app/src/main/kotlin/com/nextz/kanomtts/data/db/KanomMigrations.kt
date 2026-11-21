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
        db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId TEXT NOT NULL, locator TEXT NOT NULL, selectedText TEXT NOT NULL, beforeText TEXT, afterText TEXT, createdAt INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_bookId ON highlights(bookId)")
    }
}
