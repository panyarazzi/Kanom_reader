package com.nextz.kanomtts.reader.engine

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import javax.inject.Singleton

/** Single Readium open-pipeline shared by the metadata extractor and the reader (Plan 05 debt: was per-call). */
@Module
@InstallIn(SingletonComponent::class)
object ReadiumModule {
    @Provides @Singleton
    fun httpClient(): DefaultHttpClient = DefaultHttpClient()

    @Provides @Singleton
    fun assetRetriever(@ApplicationContext ctx: Context, http: DefaultHttpClient): AssetRetriever =
        AssetRetriever(contentResolver = ctx.contentResolver, httpClient = http)

    @Provides @Singleton
    fun publicationOpener(
        @ApplicationContext ctx: Context,
        http: DefaultHttpClient,
        assetRetriever: AssetRetriever,
    ): PublicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = ctx,
            httpClient = http,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )
}
