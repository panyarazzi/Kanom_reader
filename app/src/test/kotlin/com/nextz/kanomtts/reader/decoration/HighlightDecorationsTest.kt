package com.nextz.kanomtts.reader.decoration
import com.nextz.kanomtts.data.db.ReaderHighlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class HighlightDecorationsTest {
    @Test fun createsInactiveHighlightDecorationWithTextOnlyLocator() {
        val locator = Locator(href = Url("chapter.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(before = "before", highlight = "selected", after = "after"))
        val entity = ReaderHighlightEntity(id = 9L, bookId = "book", locator = "{}", selectedText = "selected",
            beforeText = "before", afterText = "after", createdAt = 1L)
        val decoration = HighlightDecorations.toDecorations(listOf(entity to locator)).single()
        assertEquals("hl-9", decoration.id)
        assertEquals(locator, decoration.locator)
        assertTrue(decoration.style is Decoration.Style.Highlight)
        assertEquals(false, (decoration.style as Decoration.Style.Highlight).isActive)
    }
}
