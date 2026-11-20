package com.nextz.kanomtts.reader.engine

import android.net.Uri
import com.nextz.kanomtts.text.replacement.ReplacementEngine
import com.nextz.kanomtts.text.replacement.ReplacementRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ContainerAsset
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingContainer
import org.readium.r2.shared.util.resource.map
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import javax.inject.Inject

/**
 * Opens EPUBs through the shared Readium pipeline. The reader holds the [Publication] for the
 * screen lifetime ([open] — caller closes); the extractor uses [withPublication] (open/use/close).
 */
class PublicationProvider @Inject constructor(
    private val assetRetriever: AssetRetriever,
    private val opener: PublicationOpener,
) {
    /** Opens [uri]; caller owns [Publication.close]. Returns null if the EPUB cannot be opened. */
    suspend fun open(uri: String): Publication? = withContext(Dispatchers.IO) {
        val url = Uri.parse(uri).toAbsoluteUrl() ?: return@withContext null
        val asset = assetRetriever.retrieve(url).getOrElse { return@withContext null }
        opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            return@withContext null
        }
    }

    /**
     * Opens [uri] for display, optionally wrapping the container with a Thai ZWSP transformer.
     *
     * When [thaiWordBreak] is true, HTML/XHTML resources are passed through [ThaiZwspInjector]
     * before the WebView renders them, enabling Blink to line-break Thai text at word boundaries.
     *
     * Parse-time container wrap (recon §6 fallback / §8 item 5; Task 6 Part E):
     * The transform is injected by wrapping the retrieved [ContainerAsset]'s container BEFORE
     * [PublicationOpener.open] builds the [Publication]. The Streamer therefore parses the
     * transformed container and attaches its full service set — including the positions service —
     * so the navigator's `currentLocator.locations.totalProgression` is populated and reading
     * progress persists correctly. (The prior post-open `Publication(container = ...)` rebuild
     * dropped the Streamer-added services because `servicesBuilder` is private, leaving positions
     * null and progress stuck at 0%.)
     *
     * The content/TTS path (Plan 07) ALWAYS uses [open] — do NOT swap it here, so injected ZWSP
     * never pollutes content extraction.
     */
    suspend fun openForDisplay(uri: String, thaiWordBreak: Boolean, visualRules: List<ReplacementRule>? = null): Publication? =
        withContext(Dispatchers.IO) {
            val engine = visualRules?.takeIf { it.isNotEmpty() }?.let { ReplacementEngine(it) }
            if (!thaiWordBreak && engine == null) return@withContext open(uri)

            val url = Uri.parse(uri).toAbsoluteUrl() ?: return@withContext null
            val asset = assetRetriever.retrieve(url).getOrElse { return@withContext null }

            // EPUBs retrieve as a ContainerAsset. For any non-container asset (defensive — should
            // not happen for EPUB), fall back to the un-transformed open so we never crash.
            if (asset !is ContainerAsset) {
                asset.close()
                return@withContext open(uri)
            }

            val transformer: (Url, Resource) -> Resource = { entryUrl, res ->
                val s = entryUrl.toString()
                if (s.endsWith(".xhtml", ignoreCase = true) || s.endsWith(".html", ignoreCase = true)) {
                    res.map { bytes: ByteArray ->
                        var html = bytes.decodeToString()
                        if (engine != null) html = ReplacementInjector.inject(html, engine)
                        if (thaiWordBreak) html = ThaiZwspInjector.inject(html)
                        Try.success(html.encodeToByteArray())
                    }
                } else {
                    res
                }
            }

            val wrappedAsset = ContainerAsset(
                format = asset.format,
                container = TransformingContainer(asset.container, transformer),
            )
            opener.open(wrappedAsset, allowUserInteraction = false).getOrElse {
                wrappedAsset.close()
                return@withContext null
            }
        }

    /** Opens [uri], runs [block], and guarantees [Publication.close]. Returns null if open fails. */
    suspend fun <T> withPublication(uri: String, block: suspend (Publication) -> T): T? {
        val publication = open(uri) ?: return null
        return try {
            block(publication)
        } finally {
            publication.close()
        }
    }
}
