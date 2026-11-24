package com.nextz.kanomtts.feature.library

/**
 * Display metadata extracted from an EPUB file.
 *
 * [coverBytes] is a PNG-encoded bitmap. ByteArray's default equals/hashCode uses referential
 * identity, which breaks data-class correctness for tests and caches — so we override them to
 * use structural comparison ([contentEquals] / [contentHashCode]).
 */
data class BookMetadata(
    val title: String,
    val author: String?,
    val lang: String?,
    val coverBytes: ByteArray?,
    val seriesName: String? = null,
    val seriesPosition: Double? = null,
    val description: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookMetadata) return false
        return title == other.title &&
            author == other.author &&
            lang == other.lang &&
            seriesName == other.seriesName &&
            seriesPosition == other.seriesPosition &&
            description == other.description &&
            (coverBytes?.contentEquals(other.coverBytes) ?: (other.coverBytes == null))
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (lang?.hashCode() ?: 0)
        result = 31 * result + (seriesName?.hashCode() ?: 0)
        result = 31 * result + (seriesPosition?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        return result
    }
}
