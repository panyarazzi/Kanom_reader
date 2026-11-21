package com.nextz.kanomtts.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.nextz.kanomtts.data.db.BookDao
import com.nextz.kanomtts.data.db.BookmarkDao
import com.nextz.kanomtts.data.db.KanomDatabase
import com.nextz.kanomtts.data.db.ReaderHighlightDao
import com.nextz.kanomtts.data.db.ProgressDao
import com.nextz.kanomtts.data.db.ReplacementRuleDao
import com.nextz.kanomtts.data.db.VoiceConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kanom_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KanomDatabase =
        Room.databaseBuilder(ctx, KanomDatabase::class.java, "kanom.db")
            .addMigrations(com.nextz.kanomtts.data.db.MIGRATION_1_2, com.nextz.kanomtts.data.db.MIGRATION_2_3)
            .build()

    @Provides fun provideBookDao(db: KanomDatabase): BookDao = db.bookDao()
    @Provides fun provideProgressDao(db: KanomDatabase): ProgressDao = db.progressDao()
    @Provides fun provideBookmarkDao(db: KanomDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideHighlightDao(db: KanomDatabase): ReaderHighlightDao = db.highlightDao()
    @Provides fun provideReplacementRuleDao(db: KanomDatabase): ReplacementRuleDao = db.replacementRuleDao()
    @Provides fun provideVoiceConfigDao(db: KanomDatabase): VoiceConfigDao = db.voiceConfigDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore
}
