package com.nextz.kanomtts.reader
import com.nextz.kanomtts.data.db.ReaderHighlightEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import org.readium.r2.shared.publication.Locator
object ReaderHighlightFactory {
    fun build(bookId: String, locator: Locator, now: Long): ReaderHighlightEntity? {
        val rawQuote = locator.text.highlight ?: return null
        val selected = stripZwsp(rawQuote).replace(Regex("\\s+"), " ").trim()
        if (selected.isBlank()) return null
        return ReaderHighlightEntity(bookId, LocatorSerializer.encode(locator), selected, locator.text.before, locator.text.after, now)
    }
}
