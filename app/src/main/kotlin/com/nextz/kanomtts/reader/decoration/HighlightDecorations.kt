package com.nextz.kanomtts.reader.decoration
import com.nextz.kanomtts.data.db.ReaderHighlightEntity
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
object HighlightDecorations {
    private const val DEFAULT_TINT = 0xFFFFD54F.toInt()
    fun toDecorations(highlights: List<Pair<ReaderHighlightEntity, Locator>>, tint: Int = DEFAULT_TINT): List<Decoration> =
        highlights.map { (entity, locator) ->
            Decoration(id = "hl-${entity.id}", locator = locator, style = Decoration.Style.Highlight(tint = tint, isActive = false))
        }
}
