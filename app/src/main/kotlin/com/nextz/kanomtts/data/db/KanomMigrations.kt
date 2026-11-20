package com.nextz.kanomtts.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE replacement_rules ADD COLUMN showInReader INTEGER NOT NULL DEFAULT 0")
    }
}
