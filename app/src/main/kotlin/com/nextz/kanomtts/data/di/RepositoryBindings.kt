package com.nextz.kanomtts.data.di

import com.nextz.kanomtts.data.prefs.PreferencesRepository
import com.nextz.kanomtts.data.prefs.ReaderPreferences
import com.nextz.kanomtts.data.repository.BookDeleter
import com.nextz.kanomtts.data.repository.BookmarkRepository
import com.nextz.kanomtts.data.repository.BookmarkStore
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.BookRepository
import com.nextz.kanomtts.data.repository.DefaultBookDeleter
import com.nextz.kanomtts.data.repository.ProgressReader
import com.nextz.kanomtts.data.repository.ProgressRepository
import com.nextz.kanomtts.data.repository.ProgressWriter
import com.nextz.kanomtts.reader.DefaultReaderBookSource
import com.nextz.kanomtts.reader.ReaderBookSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds @Singleton abstract fun bookReader(impl: BookRepository): BookReader
    @Binds @Singleton abstract fun progressReader(impl: ProgressRepository): ProgressReader
    @Binds @Singleton abstract fun progressWriter(impl: ProgressRepository): ProgressWriter
    @Binds @Singleton abstract fun readerBookSource(impl: DefaultReaderBookSource): ReaderBookSource
    @Binds @Singleton abstract fun readerPreferences(impl: PreferencesRepository): ReaderPreferences
    @Binds @Singleton abstract fun bookmarkStore(impl: BookmarkRepository): BookmarkStore
    @Binds @Singleton abstract fun bookDeleter(impl: DefaultBookDeleter): BookDeleter
}
