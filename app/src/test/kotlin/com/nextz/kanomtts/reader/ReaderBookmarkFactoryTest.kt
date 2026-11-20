package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReaderBookmarkFactoryTest {
    @Test fun `builds bookmark with encoded locator and the provided snippet`() {
        val loc = Locator(
            href = Url("c.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(highlight = "a spoken sentence"),
        )
        val bm = ReaderBookmarkFactory.build(
            bookId = "b1", locator = loc, snippet = "a clean excerpt", now = 42L,
        )
        assertEquals("b1", bm.bookId)
        assertEquals("a clean excerpt", bm.snippet)
        assertEquals(42L, bm.createdAt)
        assertEquals(loc, com.nextz.kanomtts.reader.engine.LocatorSerializer.decode(bm.locator))
    }
}
