package com.nextz.kanomtts.feature.library

/**
 * Seam for extracting display metadata from an EPUB file URI.
 *
 * Returns [BookMetadata] on success, or null if the file cannot be opened/parsed.
 * Implementations are expected to run their I/O on an appropriate dispatcher.
 */
interface MetadataExtractor {
    /** Opens the EPUB at [uri] and extracts display metadata, or null if it can't be read. */
    suspend fun extract(uri: String): BookMetadata?
}
