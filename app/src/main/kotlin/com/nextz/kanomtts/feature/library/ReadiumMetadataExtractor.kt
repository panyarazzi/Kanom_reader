package com.nextz.kanomtts.feature.library

import android.graphics.Bitmap
import com.nextz.kanomtts.reader.engine.PublicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * [MetadataExtractor] backed by Readium 3.3.0. Opening/closing is delegated to the shared
 * [PublicationProvider] (Plan 06 — replaces this class's former per-call DefaultHttpClient/
 * AssetRetriever construction). Reads title/author/lang + PNG cover bytes; null on open failure.
 */
class ReadiumMetadataExtractor @Inject constructor(
    private val publications: PublicationProvider,
) : MetadataExtractor {

    override suspend fun extract(uri: String): BookMetadata? = withContext(Dispatchers.IO) {
        publications.withPublication(uri) { publication ->
            val metadata = publication.metadata
            val title = metadata.title
                ?: uri.substringAfterLast('/').removeSuffix(".epub").ifBlank { "Unknown" }
            val author = metadata.authors.firstOrNull()?.name
            val lang = metadata.languages.firstOrNull()
            val coverBytes = publication.cover()?.let { bitmap ->
                ByteArrayOutputStream().also { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }.toByteArray()
            }
            BookMetadata(title = title, author = author, lang = lang, coverBytes = coverBytes)
        }
    }
}
