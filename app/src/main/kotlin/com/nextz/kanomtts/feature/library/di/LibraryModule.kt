package com.nextz.kanomtts.feature.library.di

import android.content.Context
import com.nextz.kanomtts.data.repository.BookRepository
import com.nextz.kanomtts.data.repository.BookWriter
import com.nextz.kanomtts.feature.library.EpubImporter
import com.nextz.kanomtts.feature.library.MetadataExtractor
import com.nextz.kanomtts.feature.library.ReadiumMetadataExtractor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryBindings {

    @Binds
    @Singleton
    abstract fun metadataExtractor(impl: ReadiumMetadataExtractor): MetadataExtractor

    @Binds
    @Singleton
    abstract fun bookWriter(impl: BookRepository): BookWriter
}

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {

    @Provides
    @Singleton
    fun epubImporter(
        extractor: MetadataExtractor,
        books: BookWriter,
        @ApplicationContext ctx: Context,
    ): EpubImporter = EpubImporter(
        extractor = extractor,
        books = books,
        coversDir = File(ctx.filesDir, "covers"),
        now = { System.currentTimeMillis() },
    )
}
