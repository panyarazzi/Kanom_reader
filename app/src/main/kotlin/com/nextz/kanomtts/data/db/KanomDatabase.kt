package com.nextz.kanomtts.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class, ProgressEntity::class, BookmarkEntity::class,
        ReplacementRuleEntity::class, VoiceConfigEntity::class, ReadingSessionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class KanomDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun replacementRuleDao(): ReplacementRuleDao
    abstract fun voiceConfigDao(): VoiceConfigDao
    abstract fun readingSessionDao(): ReadingSessionDao
}
