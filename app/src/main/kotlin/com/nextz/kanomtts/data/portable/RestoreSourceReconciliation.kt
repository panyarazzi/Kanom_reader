package com.nextz.kanomtts.data.portable

import com.nextz.kanomtts.data.db.BookEntity

/**
 * A source EPUB discovered beneath the folder selected for Restore.
 * extractedTitle is read from EPUB metadata when it is available.
 */
data class RestoreSourceCandidate(
    val uri: String,
    val name: String,
    val size: Long,
    val sha256: String?,
    val sourceFolder: String?,
    val extractedTitle: String?,
)

data class RestoreBookMatches(
    val book: BookEntity,
    val candidates: List<RestoreSourceCandidate>,
)

data class RestoreSourcePlan(
    val uniqueMatches: List<Pair<BookEntity, RestoreSourceCandidate>>,
    val ambiguousMatches: List<RestoreBookMatches>,
    val notFound: List<BookEntity>,
)

/**
 * Build a conservative, global matching plan without mutating the database.
 *
 * SHA-256 is authoritative whenever the backup contains it. Legacy backups
 * require exact known file size plus either the original filename or EPUB
 * metadata/title evidence. A candidate proposed for more than one book is
 * ambiguous for every affected book; it is never silently assigned.
 */
internal fun buildRestoreSourcePlan(
    books: List<BookEntity>,
    candidates: List<RestoreSourceCandidate>,
): RestoreSourcePlan {
    val possibleByBook = books.associateWith { book ->
        candidates.filter { candidate ->
            if (!book.sha256.isNullOrBlank()) {
                !candidate.sha256.isNullOrBlank() &&
                    candidate.sha256.equals(book.sha256, ignoreCase = true)
            } else {
                val sizeMatches = book.fileSize > 0L &&
                    candidate.size > 0L &&
                    book.fileSize == candidate.size
                val fileNameMatches = !book.sourceFileName.isNullOrBlank() &&
                    candidate.name.equals(book.sourceFileName, ignoreCase = true)
                val titleMatches = candidate.extractedTitle.equals(book.title, ignoreCase = true) ||
                    candidate.name.substringBeforeLast('.', candidate.name)
                        .equals(book.title, ignoreCase = true)
                sizeMatches && (fileNameMatches || titleMatches)
            }
        }
    }

    val candidateUseCount = possibleByBook.values.flatten()
        .groupingBy { it.uri }
        .eachCount()

    val unique = mutableListOf<Pair<BookEntity, RestoreSourceCandidate>>()
    val ambiguous = mutableListOf<RestoreBookMatches>()
    val missing = mutableListOf<BookEntity>()

    books.forEach { book ->
        val matches = possibleByBook[book].orEmpty()
        when {
            matches.isEmpty() -> missing += book
            matches.size == 1 && candidateUseCount[matches.single().uri] == 1 ->
                unique += book to matches.single()
            else -> ambiguous += RestoreBookMatches(book, matches)
        }
    }

    return RestoreSourcePlan(
        uniqueMatches = unique,
        ambiguousMatches = ambiguous,
        notFound = missing,
    )
}
