package com.nextz.kanomtts.reader.decoration

import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class BookmarkDecorationsTest {

    private val loc = Locator(
        href = Url("ch1.xhtml")!!,
        mediaType = MediaType.XHTML,
        text = Locator.Text(highlight = "some text"),
    )

    @Test fun `toDecorations maps valid bookmark to Underline decoration`() {
        val entity = BookmarkEntity(
            id = 7L,
            bookId = "book1",
            locator = LocatorSerializer.encode(loc),
            snippet = "some text",
            createdAt = 100L,
        )
        val decorations = BookmarkDecorations.toDecorations(listOf(entity))
        assertEquals(1, decorations.size)
        val d = decorations[0]
        assertEquals("bm-7", d.id)
        assertEquals(loc, d.locator)
        assertTrue(d.style is Decoration.Style.Underline)
        val underline = d.style as Decoration.Style.Underline
        assertEquals(0xFFFF5E9C.toInt(), underline.tint)
        assertEquals(false, underline.isActive)
    }

    @Test fun `toDecorations filters out un-decodable locator`() {
        val bad = BookmarkEntity(
            id = 1L,
            bookId = "book1",
            locator = "not-json",
            snippet = "",
            createdAt = 1L,
        )
        val result = BookmarkDecorations.toDecorations(listOf(bad))
        assertTrue(result.isEmpty())
    }

    @Test fun `toDecorations accepts custom tint`() {
        val entity = BookmarkEntity(
            id = 3L,
            bookId = "book1",
            locator = LocatorSerializer.encode(loc),
            snippet = "x",
            createdAt = 10L,
        )
        val customTint = 0xFF00FF00.toInt()
        val decorations = BookmarkDecorations.toDecorations(listOf(entity), roseArgb = customTint)
        val underline = decorations[0].style as Decoration.Style.Underline
        assertEquals(customTint, underline.tint)
    }
}
