package com.nextz.kanomtts.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class, ProgressEntity::class, BookmarkEntity::class, ReaderHighlightEntity::class,
        ReplacementRuleEntity::class, VoiceConfigEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class KanomDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): ReaderHighlightDao
    abstract fun replacementRuleDao(): ReplacementRuleDao
    abstract fun voiceConfigDao(): VoiceConfigDao
}
