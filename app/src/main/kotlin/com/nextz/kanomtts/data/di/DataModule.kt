package com.nextz.kanomtts.data.di
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.nextz.kanomtts.data.db.*
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
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): KanomDatabase =
        Room.databaseBuilder(ctx, KanomDatabase::class.java, "kanom.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()
    @Provides fun provideBookDao(db: KanomDatabase): BookDao = db.bookDao()
    @Provides fun provideProgressDao(db: KanomDatabase): ProgressDao = db.progressDao()
    @Provides fun provideBookmarkDao(db: KanomDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideReplacementRuleDao(db: KanomDatabase): ReplacementRuleDao = db.replacementRuleDao()
    @Provides fun provideVoiceConfigDao(db: KanomDatabase): VoiceConfigDao = db.voiceConfigDao()
    @Provides fun provideReadingSessionDao(db: KanomDatabase): ReadingSessionDao = db.readingSessionDao()
    @Provides fun provideBookTagDao(db: KanomDatabase): BookTagDao = db.bookTagDao()
    @Provides fun provideBookTagLibraryDao(db: KanomDatabase): BookTagLibraryDao = db.bookTagLibraryDao()
    @Provides fun provideBookNoteDao(db: KanomDatabase): BookNoteDao = db.bookNoteDao()
    @Provides @Singleton fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore
}
