package com.nextz.kanomtts.reader

import com.nextz.kanomtts.data.db.BookEntity
import com.nextz.kanomtts.data.db.ProgressEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.BookWriter
import com.nextz.kanomtts.data.repository.ProgressReader
import com.nextz.kanomtts.data.repository.ProgressWriter
import com.nextz.kanomtts.reader.engine.PublicationProvider
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.readium.r2.shared.publication.Publication
import javax.inject.Inject

/** Seam the ReaderViewModel depends on, so its pre-render state machine is unit-testable. */
interface ReaderBookSource {
    suspend fun book(bookId: String): BookEntity?
    suspend fun open(uri: String): Publication?
    /** Opens [uri] for display, with optional replacement injection and Thai ZWSP injection. See [PublicationProvider.openForDisplay]. */
    suspend fun openForDisplay(uri: String, thaiWordBreak: Boolean, visualRules: List<ReplacementRule>? = null): Publication?
    suspend fun progress(bookId: String): ProgressEntity?
    suspend fun saveProgress(progress: ProgressEntity)
    /**
     * Partial-column update: save the reading locator + percent WITHOUT touching ttsLocator.
     * Avoids the read-modify-write race with the TTS locator writer. (I2)
     */
    suspend fun saveReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long)
    suspend fun touchOpened(book: BookEntity)
}

class DefaultReaderBookSource @Inject constructor(
    private val books: BookReader,
    private val bookWriter: BookWriter,
    private val publications: PublicationProvider,
    private val progressReader: ProgressReader,
    private val progressWriter: ProgressWriter,
) : ReaderBookSource {
    override suspend fun book(bookId: String): BookEntity? = books.get(bookId)
    override suspend fun open(uri: String): Publication? = publications.open(uri)
    override suspend fun openForDisplay(uri: String, thaiWordBreak: Boolean, visualRules: List<ReplacementRule>?): Publication? =
        publications.openForDisplay(uri, thaiWordBreak, visualRules)
    override suspend fun progress(bookId: String): ProgressEntity? = progressReader.get(bookId)
    override suspend fun saveProgress(progress: ProgressEntity) = progressWriter.save(progress)
    override suspend fun saveReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long) =
        progressWriter.saveReadingLocator(bookId, locator, percent, updatedAt)
    override suspend fun touchOpened(book: BookEntity) =
        bookWriter.add(book.copy(lastOpenedAt = System.currentTimeMillis()))
}
